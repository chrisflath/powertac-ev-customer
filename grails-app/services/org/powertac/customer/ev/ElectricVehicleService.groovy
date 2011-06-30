package org.powertac.customer.ev

import org.powertac.cutomer.ev.ElectricVehicle
import org.powertac.common.enumerations.PowerType
import org.powertac.common.enumerations.CustomerType
import org.powertac.common.CustomerInfo
import org.powertac.common.PluginConfig

class ElectricVehicleService {

  static transactional = true

  def init() {
    ElectricVehicle ev = new ElectricVehicle()

    // Find and set config
    ev.config = PluginConfig.findByRoleName("ElectricVehicle")

    // Create customer info
    CustomerInfo info = new CustomerInfo(name: "ElectricVehicle",
        customerType: CustomerType.CustomerElectricVehicle,
        powerTypes: [PowerType.CONSUMPTION]).save()
    ev.customerInfo = info

    // Workaround #288
    ev.init()
    ev.subscribeDefault()

    // Save and log errors
    if (!ev.save()) {
      ev.errors.allErrors.each { log.error it }
    }

    // load data
    ev.loadData()
    ev.performImmediateLoadingStrategy()

    //log.error ElectricVehicle.getAll()
  }
}
