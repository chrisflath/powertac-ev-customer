package org.powertac.cutomer.ev

import org.powertac.common.AbstractCustomer
import org.powertac.common.PluginConfig
import au.com.bytecode.opencsv.CSVReader
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Instant
import org.powertac.common.Competition

class ElectricVehicle extends AbstractCustomer {

  PluginConfig config
  // Inject service to get access to tariff rates (for evaluation purposes only)
  def electricVehicleInitializationService

  static hasMany = [timeslots: ElectricVehicleTimeslot]

  static constraints = {
    config(nullable: false)
  }

  @Override
  void step() {
    super.step()
    log.error "some step here"
  }

  // Load the driving profile
  def loadData() {
    DateTime competitionBaseTime
    //competitionBaseTime = Competition.currentCompetition().simulationBaseTime.toDateTime()
    // Assume Mon, 2011/07/04 for evaluation purposes
    competitionBaseTime = new DateTime(2011, 7, 4, 0, 0, 0, 0, DateTimeZone.UTC)

    // Load generated driving profile
    CSVReader reader = new CSVReader(new FileReader("/Users/ddauer/Desktop/Profiles/Employee/Profile0.csv"))
    String[] nextLine
    String[] previousLine

    // Count lines since we need to group 4 lines each
    def count = 0

    // Keep track of timeslots
    ElectricVehicleTimeslot previousTimeslot
    ElectricVehicleTimeslot currentTimeslot

    while ((nextLine = reader.readNext()) != null) {
      // Skip first line (header)
      if (nextLine[0] == "ID") { continue }

      // Extract values from csv
      //def id = nextLine[0].stripIndent() // not needed
      //def jobType = nextLine[1].stripIndent()
      //def weekday = nextLine[2].stripIndent()
      //def date = nextLine[3].stripIndent()
      def km = nextLine[4].stripIndent() as BigDecimal
      def tripType = nextLine[5].stripIndent() as String

      // Create timeslot if necessary
      if (count == 0) { // :00
        if (previousTimeslot) {
          currentTimeslot = new ElectricVehicleTimeslot(dateTime: previousTimeslot.dateTime.plusHours(1))
          currentTimeslot.trip = tripType
          currentTimeslot.atHome = previousTimeslot.atHome
          currentTimeslot.driving = (tripType != "NoTrip")
          currentTimeslot.charging = false
          currentTimeslot.km = km

          if (previousTimeslot.trip == "ToHome" && tripType == "NoTrip") { // check if we are at home
            currentTimeslot.atHome = true
          }

          currentTimeslot.save()
          this.addToTimeslots(currentTimeslot)
        } else {
          // Timeslot 0, time based on comeptitionBaseTime
          currentTimeslot = new ElectricVehicleTimeslot(dateTime: competitionBaseTime.plusHours(1))
          // Since this is the very first timeslot, we can reason the following values
          currentTimeslot.trip = tripType
          currentTimeslot.atHome = (tripType == "NoTrip")
          currentTimeslot.driving = (tripType != "NoTrip")
          currentTimeslot.charging = false
          currentTimeslot.stateOfCharge = config?.configuration?.capacity_kwh as BigDecimal
          currentTimeslot.save()
          this.addToTimeslots(currentTimeslot)
        }


      } else { // :15, :30, :45 - update within timeslot
        currentTimeslot.km += km // Sum km up

        if (tripType != "NoTrip") { // Leave timeslot state untouched unless we are not at home (!= "NoTrip").
          currentTimeslot.atHome = false
          currentTimeslot.driving = true
          currentTimeslot.trip = tripType
        }

        currentTimeslot.save()
      }


      count++
      if (count == 4) {
        count = 0
      }

      previousLine = nextLine
      previousTimeslot = currentTimeslot
    }
  }

  def performImmediateLoadingStrategy() {
    // iterate over all timeslots
    def allTimeslots = ElectricVehicleTimeslot.getAll()
    def hourlyRates = electricVehicleInitializationService.hourlyRateTariffRates()
    allTimeslots.eachWithIndex { ts, i ->
      // No need to calc the needed amount for the first timeslot since we assune we're fully charged
      if (i != 0) {
        // g(t)
        def loadingPercentage = calcLoadingPercentage(ts, allTimeslots.get(i - 1))
        // SOC(t)
        def capacity = config?.configuration?.capacity_kwh as BigDecimal // C
        def chargingTime = config?.configuration?.requiredChargingHours as BigDecimal // v
        def efficiency = config?.configuration?.batteryEfficiency as BigDecimal // eta
        def avgConsumption = config?.configuration?.avgConsumption as BigDecimal // PC in kWh/km
        def maxLoadingAmountPerTimeslot = (capacity / (chargingTime * efficiency)) // C/v

        def lastStateOfCharge = allTimeslots.get(i - 1).stateOfCharge /* SOC(t-1) */
        def consumption = (maxLoadingAmountPerTimeslot * loadingPercentage) /* consumption */
        def demand = ts.km * avgConsumption
        def stateOfCharge = lastStateOfCharge + consumption - demand

        // finally save soc
        ts.stateOfCharge = stateOfCharge
        ts.energyDemand = consumption

        // calc costs
        def rate = hourlyRates.get(ts.dateTime.getHourOfDay()) as BigDecimal
        ts.estimatedCost = consumption * rate

        ts.save()
      }
    }

    // Debugging, log for xls
    ElectricVehicleTimeslot.getAll().eachWithIndex { ts, i ->
      // debug: 4 times because driving profile is 4 times more accurate
      1.times {
        print i
        print '\t'
        print ts.dateTime
        print '\t'
        print ts.atHome
        print '\t'
        print ts.km
        print '\t'
        print ts.stateOfCharge
        print '\t'
        print ts.energyDemand
        print '\t'
        print ts.estimatedCost
        print '\t'
        println electricVehicleInitializationService.hourlyRateTariffRates().get(ts.dateTime.getHourOfDay()) as BigDecimal
      }
    }
  }

  // Calculate how much should be loaded (0..1) // g(t)
  def calcLoadingPercentage(ElectricVehicleTimeslot currentTimeslot, ElectricVehicleTimeslot previousTimeslot) {
    // We assume that we can only load at home
    if (currentTimeslot.atHome) {

      def oldStateOfCharge = previousTimeslot.stateOfCharge // SOC(t-1)
      def capacity = config?.configuration?.capacity_kwh as BigDecimal // C
      def chargingTime = config?.configuration?.requiredChargingHours as BigDecimal // v
      def efficiency = config?.configuration?.batteryEfficiency as BigDecimal // eta
      def maxLoadingAmountPerTimeslot = (capacity / (chargingTime * efficiency)) // C/(v*eta)

      // We can't charge the full amount per timeslot, so determine how much we actually can
      if ((oldStateOfCharge + maxLoadingAmountPerTimeslot) > capacity) {
        return ((capacity - oldStateOfCharge) / maxLoadingAmountPerTimeslot)
      } else {
        // We actually can load the maximum possible amount
        return new BigDecimal(1.0)
      }

    } else {
      return new BigDecimal(0.0)
    }
  }

}
