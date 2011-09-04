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

class ElectricVehicle extends AbstractCustomer {

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

  // Load the driving profile
//  def loadData() {
//    DateTime competitionBaseTime
//    competitionBaseTime = Competition.currentCompetition().simulationBaseTime.toDateTime()
//    // Assume Mon, 2011/07/04 for evaluation purposes
//    //competitionBaseTime = new DateTime(2011, 7, 4, 0, 0, 0, 0, DateTimeZone.UTC)
//
//    // Load generated driving profile
//    CSVReader reader = new CSVReader(new FileReader(config?.configuration?.drivingProfile))
//    String[] nextLine
//    String[] previousLine
//
//    // Count lines since we need to group 4 lines each
//    def count = 0
//
//    // Keep track of timeslots
//    ElectricVehicleTimeslot previousTimeslot
//    ElectricVehicleTimeslot currentTimeslot
//
//    while ((nextLine = reader.readNext()) != null) {
//      // Skip first line (header)
//      if (nextLine[0] == "ID") { continue }
//
//      // Extract values from csv
//      //def id = nextLine[0].stripIndent() // not needed
//      //def jobType = nextLine[1].stripIndent()
//      //def weekday = nextLine[2].stripIndent()
//      //def date = nextLine[3].stripIndent()
//      def km = nextLine[4].stripIndent() as BigDecimal
//      def tripType = nextLine[5].stripIndent() as String
//
//      // Create timeslot if necessary
//      if (count == 0) { // :00
//        if (previousTimeslot) {
//          currentTimeslot = new ElectricVehicleTimeslot(dateTime: previousTimeslot.dateTime.plusHours(1))
//          currentTimeslot.trip = tripType
//          currentTimeslot.atHome = previousTimeslot.atHome
//          currentTimeslot.driving = (tripType != "NoTrip")
//          currentTimeslot.charging = false
//          currentTimeslot.km = km
//
//          if (previousTimeslot.trip == "ToHome" && tripType == "NoTrip") { // check if we are at home
//            currentTimeslot.atHome = true
//          }
//
//          currentTimeslot.save()
//          this.addToTimeslots(currentTimeslot)
//        } else {
//          // Timeslot 0, time based on comeptitionBaseTime
//          currentTimeslot = new ElectricVehicleTimeslot(dateTime: competitionBaseTime.plusHours(1))
//          // Since this is the very first timeslot, we can reason the following values
//          currentTimeslot.trip = tripType
//          currentTimeslot.atHome = (tripType == "NoTrip")
//          currentTimeslot.driving = (tripType != "NoTrip")
//          currentTimeslot.charging = false
//          currentTimeslot.stateOfCharge = config?.configuration?.capacity_kwh as BigDecimal
//          currentTimeslot.save()
//          this.addToTimeslots(currentTimeslot)
//        }
//
//
//      } else { // :15, :30, :45 - update within timeslot
//        currentTimeslot.km += km // Sum km up
//
//        if (tripType != "NoTrip") { // Leave timeslot state untouched unless we are not at home (!= "NoTrip").
//          currentTimeslot.atHome = false
//          currentTimeslot.driving = true
//          currentTimeslot.trip = tripType
//        }
//
//        currentTimeslot.save()
//      }
//
//
//      count++
//      if (count == 4) {
//        count = 0
//      }
//
//      previousLine = nextLine
//      previousTimeslot = currentTimeslot
//    }
//  }

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
        powerTypes: [PowerType.CONSUMPTION]).save()

    this.customerInfo = info
    this.custId = customerInfo.getId()

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

    // HEURISTIC HERE


    DateTime currentDateTime = timeService.getCurrentDateTime()
    int day = currentDateTime.getDayOfWeek()
    int hour = currentDateTime.getHourOfDay()

    // Find corresponding EVTimeslot
//    ElectricVehicleTimeslot evts
//    Boolean found = false
//    ElectricVehicleTimeslot.getAll().each { ts ->
//      if (!found) {
//        DateTime dt = ts.dateTime
//        if (dt.getDayOfWeek() == day && dt.getHourOfDay() == hour) {
//          evts = ts
//          found = true
//        }
//      }
//    }
//
//    if (!found) {
//      log.error "No timeslot found"
//    }
//
//    // There should be only 1 subscription
//    def subscriptionList = subscriptions as List
//
//    if (subscriptionList.size() == 1) {
//      TariffSubscription sub = subscriptionList.get(0)
//      double power = evts?.energyDemand?.toDouble()
//      sub.usePower(power)
//      log.info "EV using $power"
//    } else {
//      log.error "More than 1 subscription"
//    }
  }

  @Override
  double[][] getBootstrapData() {
    return [0][0]
  }
}
