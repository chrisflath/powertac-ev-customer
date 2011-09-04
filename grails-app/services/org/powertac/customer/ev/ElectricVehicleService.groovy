package org.powertac.customer.ev

import org.powertac.cutomer.ev.ElectricVehicle
import org.powertac.common.enumerations.PowerType
import org.powertac.common.enumerations.CustomerType
import org.powertac.common.CustomerInfo
import org.powertac.common.PluginConfig
import org.joda.time.Instant
import org.powertac.common.interfaces.TimeslotPhaseProcessor

class ElectricVehicleService implements TimeslotPhaseProcessor {

  static transactional = true

  def competitionControlService

  def init() {
    competitionControlService.registerTimeslotPhase(this, 1)

    // Workaround #288
    ElectricVehicle.list()*.subscribeDefault()
  }

  void activate(Instant now, int phaseNumber) {
    ElectricVehicle.list()*.step()
  }


}
