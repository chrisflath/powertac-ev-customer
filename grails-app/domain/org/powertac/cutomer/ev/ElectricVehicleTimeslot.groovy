package org.powertac.cutomer.ev

import org.joda.time.DateTime

class ElectricVehicleTimeslot {

  DateTime dateTime

  Boolean atHome // x(t)
  Boolean driving // y(t)
  Boolean charging // z(t)
  BigDecimal km = new BigDecimal(0.0)
  String trip

  BigDecimal stateOfCharge // kWh
  BigDecimal energyDemand // kWh
  BigDecimal estimatedCost // EUR

  static belongsTo = [electricVehicle: ElectricVehicle]

  static constraints = {
    dateTime(nullable: false)
    atHome(nullable: false)
    driving(nullable: false)
    charging(nullable: false)
    km(nullable: false)
    trip(nullable: false)
    // These values are set later, so they need to be nullable at object creation
    stateOfCharge(nullable: true, scale: 8)
    energyDemand(nullable: true, scale: 8)
    estimatedCost(nullable: true, scale: 2)
  }
}
