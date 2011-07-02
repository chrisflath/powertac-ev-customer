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
    CSVReader reader = new CSVReader(new FileReader("/Users/ddauer/Desktop/Profiles/Unemployed/Profile9.csv"))
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

  def performImmediateCharging() {
    // iterate over all timeslots
    def allTimeslots = ElectricVehicleTimeslot.getAll()
    def hourlyRates = electricVehicleInitializationService.hourlyRateTariffRates()
    allTimeslots.eachWithIndex { ts, i ->
      // No need to calc the needed amount for the first timeslot since we assune we're fully charged
      if (i != 0) {
        // g(t)
        def loadingPercentage = calcImmediateChargingPercentage(ts, allTimeslots.get(i - 1))
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
        ts.charging = (consumption > 0)

        // calc costs
        def rate = hourlyRates.get(ts.dateTime.getHourOfDay()) as BigDecimal
        ts.rate = rate
        ts.estimatedCost = consumption * rate

        ts.save()
      }
    }
  }

  // Calculate how much should be loaded (0..1) // g(t)
  def calcImmediateChargingPercentage(ElectricVehicleTimeslot currentTimeslot, ElectricVehicleTimeslot previousTimeslot) {
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

  def performSmartCharging() {
    // use immediate charging first
    performImmediateCharging()

    // temporary variables
    def possibleChargingTimeslotsList = []

    // analyze timeslots as follows
    def allTimeslots = ElectricVehicleTimeslot.getAll()
    allTimeslots.eachWithIndex { ts, i ->
      // Find all charging timeslots
      if (ts.atHome && ts.charging) {
        possibleChargingTimeslotsList.add(ts)
      }

      // If we have at least one timeslot to charge, add all following timeslots
      if (possibleChargingTimeslotsList.size() > 0 && !possibleChargingTimeslotsList.contains(ts)) {
        possibleChargingTimeslotsList.add(ts)
      }

      // We previously had found timeslots but are driving now, so we've got out interval
      // Also force clearing at the very end
      if ((ts.driving && (possibleChargingTimeslotsList.size() > 0)) || (allTimeslots.size() - 1 == i)) {
        // Load shifting
        def timeslotBeforeFirstRequiredCharge = ElectricVehicleTimeslot.findById((possibleChargingTimeslotsList.get(0).id - 1))
        shiftLoad(possibleChargingTimeslotsList, timeslotBeforeFirstRequiredCharge.stateOfCharge)
        // Clean up
        possibleChargingTimeslotsList.clear()
      }
    }
  }

  def shiftLoad(List timeslots, BigDecimal lastStateOfCharge) {
    // Get and sort timeslots with energy demand
    def timeslotsWithEnergyDemand = timeslots.findAll { ElectricVehicleTimeslot ts -> ts.energyDemand > 0.0 }
    timeslotsWithEnergyDemand.sort { ElectricVehicleTimeslot ts -> ts.energyDemand }
    Collections.reverse(timeslotsWithEnergyDemand)

    // find corresponding number of cheapest timeslots
    def cheapestTimeslots = timeslots.clone()
    cheapestTimeslots.sort { ElectricVehicleTimeslot ts -> ts.rate }

    // Need to store updates temporarily to avoid data inconsistency issues
    // Format: key = timeslot id / value =  new energy demand
    def temporaryDemandMap = [:]
    // Match highest demand with cheapest prices
    timeslotsWithEnergyDemand.eachWithIndex { ElectricVehicleTimeslot ts, i ->
      ElectricVehicleTimeslot cheapestTimeslot = cheapestTimeslots.get(i)

      // Skip changes if we already have the cheapest timeslot
      if (ts.id != cheapestTimeslot.id) {
        // Save shift volume
        temporaryDemandMap[cheapestTimeslot.id] = ts.energyDemand
      }
    }

    // Finally update all timeslots in selected interval
    def stateOfCharge = lastStateOfCharge
    timeslots.eachWithIndex { ElectricVehicleTimeslot ts, i ->
      // update if we have saved id before
      if (temporaryDemandMap[ts.id] != null) {
        ts.energyDemand = temporaryDemandMap[ts.id]
        ts.charging = true
        ts.estimatedCost = ts.energyDemand * ts.rate
        stateOfCharge += ts.energyDemand
        ts.stateOfCharge = stateOfCharge
      } else {
        // update load appropriately (= reset)
        ts.energyDemand = new BigDecimal(0.0)
        ts.charging = false
        ts.estimatedCost = new BigDecimal(0.0)
        ts.stateOfCharge = stateOfCharge
      }

      // TODO: update SOC here - not really needed since the analysis in complete anyway
      // The SOC field is inconsistent and probably should not be used anymore

      ts.save()
    }
  }

  def printTimeslotsForEvaluation() {
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
        println ts.rate
      }
    }
  }

}
