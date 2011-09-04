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

class ElectricVehicle extends AbstractCustomer {

  // NOTE: Competition start time should be set to a Monday, 0:00 in Competition.groovy or Web UI!

  String name
  BigDecimal capacity_kwh = 35.0 // default value if not found in config
  BigDecimal avgConsumption_kwh = 14.0 // default value if not found in config
  BigDecimal priceThreshold = 0.5 // default value if not found in config
  BigDecimal socThreshold = 0.5 // default value if not found in config

  BigDecimal currentSOC = 35.0

  Integer profileId = 0 // [0..999]
  Integer profileRowOffset = 0

  PluginConfig config

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

    currentSOC = capacity_kwh // Initial SOC = full

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
    log.error "step() begin - ${toString()}, timeslot: ${timeService.currentTime}"
    consumePower()
    log.error "step() end - ${toString()}, timeslot: ${timeService.currentTime}"
  }

  @Override
  void consumePower() {
    // Collect decision data in addition to existing instance variables (soc, thresholds)

    // Get current time
    Instant now = timeService.currentTime

    // Get current price
    // Assumption: There is only one (default) tariff available
    List <TariffSubscription> subscriptionList = subscriptions as List
    if (subscriptionList.size() > 1) {
      log.error "there should be only one subscription: ${subscriptionList}"
    }
    TariffSubscription subscription = subscriptionList.get(0)
    Tariff tariff = subscription.tariff
    double price = tariff.getUsageCharge(now)

    log.error "price is ${price}"

    // Determine columns
    int distanceColumn = profileId*2
    int typeColumn = distanceColumn + 1

    log.error "check columns ${distanceColumn} and ${typeColumn}"

    // Report power usage
    // use bigDecimalInstance.toDouble()
    subscription.usePower(123.123)


  }

  @Override
  double[][] getBootstrapData() {
    return [0][0]
  }
}
