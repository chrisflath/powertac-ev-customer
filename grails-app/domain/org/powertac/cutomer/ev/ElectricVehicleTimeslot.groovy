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

  static belongsTo = [electricVehicle: ElectricVehicle]

  static constraints = {
    dateTime(nullable: false)
    atHome(nullable: false)
    driving(nullable: false)
    charging(nullable: false)
    km(nullable: false)
    trip(nullable: false)
    stateOfCharge(nullable: false)
  }
}
