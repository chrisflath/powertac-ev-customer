package org.powertac.cutomer.ev

import org.powertac.common.AbstractCustomer
import org.powertac.common.PluginConfig
import au.com.bytecode.opencsv.CSVReader
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.powertac.common.Competition
import org.powertac.common.TariffSubscription
import org.powertac.common.enumerations.PowerType
import org.powertac.common.enumerations.CustomerType
import org.powertac.common.CustomerInfo
import org.joda.time.Instant
import org.powertac.common.Tariff
import java.text.NumberFormat
import org.powertac.common.msg.CustomerReport
import java.math.RoundingMode

class ElectricVehicle extends AbstractCustomer {

  // NOTE: Competition start time should be set to a Monday, 0:00 in Competition.groovy or Web UI!
  // NOTE: 21.06.2010 is a Monday.

  String name
  BigDecimal capacity_kwh = 35.0 // default value if not found in config
  BigDecimal avgConsumption_kwh = 14.0 // default value if not found in config
  BigDecimal priceThreshold = 0.5 // default value if not found in config
  BigDecimal socThreshold = 0.5 // default value if not found in config
  BigDecimal maxChargingSpeedPerHour = 11.0 // default value if not found in config

  BigDecimal currentSOC = 35.0

  Integer profileId = 0 // [0..999]
  Integer profileRowOffset = 0

  PluginConfig config
  def visualizationProxyService
  static constraints = {
    config(nullable: false)
  }

  void configure(PluginConfig config) {
    this.config = config
    name = config.name

    profileId = getValidConfig(config, 'profileId', profileId.toString()).toInteger()
    capacity_kwh = getValidConfig(config, 'capacity_kwh', capacity_kwh.toString()).toBigDecimal()
    avgConsumption_kwh = getValidConfig(config, 'avgConsumption_kwh', avgConsumption_kwh.toString()).toBigDecimal()
    priceThreshold = getValidConfig(config, 'priceThreshold', priceThreshold.toString()).toBigDecimal()
    socThreshold = getValidConfig(config, 'socThreshold', socThreshold.toString()).toBigDecimal()
    maxChargingSpeedPerHour = getValidConfig(config, 'maxChargingSpeedPerHour', maxChargingSpeedPerHour.toString()).toBigDecimal()

    currentSOC = capacity_kwh*0.75 // Initial SOC = full

    // Every customer needs to have a customer info
    CustomerInfo info = new CustomerInfo(name: name,
        customerType: CustomerType.CustomerElectricVehicle,
        powerTypes: [PowerType.CONSUMPTION]) // PowerTypes never saved correctly! Workaround in CustomerInfo.

    if (!info.validate()) {
      info.errors.allErrors.each {log.error(it.toString())}
    }
    info.save()

    this.customerInfo = info
    this.custId = customerInfo.getId()
    this.save()
  }

  String getValidConfig(PluginConfig config, String name, String defaultValue) {
    String result = config.configuration[name]
    if (result == null) {
      log.error "No config value for ${name}: using ${defaultValue}"
      return defaultValue
    }
    else {
      return result
    }
  }

  /*
 Overriding this is required so we can report our own power consumption
  */

  @Override
  void step() {
//    log.error "step() begin - ${toString()}, timeslot: ${timeService.currentTime}"
    consumePower()
//    log.error "step() end - ${toString()}, timeslot: ${timeService.currentTime}"
  }

  @Override
  void consumePower() {
    // Collect decision data in addition to existing instance variables (soc, thresholds)

    // Get current time
    Instant now = timeService.currentTime

    // Get current price
    // Assumption: There is only one (default) tariff available
    List<TariffSubscription> subscriptionList = subscriptions as List
    if (subscriptionList.size() > 1) {
      log.error "there should be only one subscription: ${subscriptionList}"
    }
    TariffSubscription subscription = subscriptionList.get(0)
    Tariff tariff = subscription.tariff
    double price = tariff.getUsageCharge(now)

    log.error "price is ${price}"

    // Determine columns
    int distanceColumn = profileId * 2
    int typeColumn = distanceColumn + 1

//    log.error "check columns ${distanceColumn} and ${typeColumn}"

    // Load CSV Data

    // Loading the following each time might be incredibly slow...
    // Load profiles from /powertac-server/rawProfiles.csv
    def baseDir = System.properties.getProperty('base.dir')
    def csvFile = "${baseDir}/rawProfiles.csv"
    CSVReader reader = new CSVReader(new FileReader(csvFile), (char) 59) // 59 = ASCII ';'
    List<String[]> profiles = reader.readAll()

    BigDecimal profileDistance = new BigDecimal(0.0)
    String profileType = "HOME"

    //log.error "offset begin ${profileRowOffset}"

    4.times {
      // Load data
      String[] quarterHour = profiles.get(profileRowOffset++)
      NumberFormat nf = NumberFormat.getInstance(Locale.GERMAN)
      Number parsedNumber = nf.parse(quarterHour[distanceColumn])
      BigDecimal quarterDistance = new BigDecimal(parsedNumber)
      String quarterType = quarterHour[typeColumn]

//      log.error "${profileRowOffset}: distance ${quarterDistance} type ${quarterType}"

      // Verify and aggregate
      if (quarterDistance > 0) {
        profileDistance += quarterDistance
      }

      if (profileType == "DRIVING" && quarterType != "DRIVING") {
        // Once 15min are set to driving, the whole hour needs to remain driving
      } else {
        // Set profile type
        profileType = quarterType
      }
    }

//    log.error "offset end ${profileRowOffset}"
    //    log.error "driving ${profileDistance} with ${profileType}"

    BigDecimal powerUsage = profileDistance * avgConsumption_kwh
    powerUsage.setScale(2, RoundingMode.HALF_EVEN)

//    log.error "powerUsage ${powerUsage}"

    if (profileType == "DRIVING") {
      // update soc
      currentSOC -= powerUsage
      currentSOC.setScale(2, RoundingMode.HALF_EVEN)
      //TODO: Maybe make sure that SOC > 0
    }

    if (profileType == "HOME" && currentSOC < capacity_kwh) {
      if (currentSOC < socThreshold || price < priceThreshold) {
        def chargeAmount = new BigDecimal(Math.min(maxChargingSpeedPerHour, capacity_kwh - currentSOC))
        chargeAmount.setScale(2, RoundingMode.HALF_EVEN)
        subscription.usePower(chargeAmount.toDouble())
        currentSOC += chargeAmount
        log.error("charged ${chargeAmount} at price ${price}")
        CustomerReport msg = new CustomerReport(name: this.customerInfo.name, powerUsage: new BigDecimal(chargeAmount))
        msg.save()
        visualizationProxyService.forwardMessage(msg)
      }

    } else {
      log.error("no charging soc is ${currentSOC}")
      CustomerReport msg = new CustomerReport(name: this.customerInfo.name, powerUsage: new BigDecimal(0.0))
      msg.save()
      visualizationProxyService.forwardMessage(msg)
    }

  }

  @Override
  double[][] getBootstrapData() {
    return [0][0]
  }
}
