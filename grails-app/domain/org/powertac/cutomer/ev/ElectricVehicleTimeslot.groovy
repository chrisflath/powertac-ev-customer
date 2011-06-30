package org.powertac.cutomer.ev

import org.joda.time.Instant
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

class ElectricVehicleTimeslot {

  Instant instant

  static belongsTo = [electricVehicle: ElectricVehicle]

  static constraints = {
    instant(nullable:false)
  }
}
