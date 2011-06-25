package org.powertac.cutomer.ev

import org.powertac.common.AbstractCustomer
import org.powertac.common.PluginConfig

class ElectricVehicle extends AbstractCustomer {

  PluginConfig config

  static constraints = {
    config(nullable: false)
  }

  @Override
  void step() {
    super.step()
    log.error "some step here"
  }
}
