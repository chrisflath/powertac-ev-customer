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
  BigDecimal stateOfCharge // kWh

  static hasMany = [timeslots: ElectricVehicleTimeslot]

  static constraints = {
    config(nullable: false)
    stateOfCharge(nullable: false)
  }

  @Override
  void step() {
    super.step()
    log.error "some step here"
  }

  @Override
  void init() {
    super.init()
    // Set stateOfCharge to fully loaded
    stateOfCharge = config?.configuration?.capacity_kwh as BigDecimal
  }

  // Load the driving profile
  def loadData = {
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

    ElectricVehicleTimeslot.getAll().each { ts ->
      4.times {
        print ts.atHome
        print '\t'
        print ts.driving
        print '\t'
        println ts.km
      }

    }

  }

}
