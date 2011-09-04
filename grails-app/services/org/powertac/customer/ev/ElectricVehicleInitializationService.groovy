package org.powertac.customer.ev

import org.powertac.common.interfaces.InitializationService
import org.powertac.common.Competition
import org.powertac.common.PluginConfig
import org.powertac.cutomer.ev.ElectricVehicle

class ElectricVehicleInitializationService implements InitializationService {

  static transactional = true

  def electricVehicleService

  void setDefaults() {
    // At simulator startup
    build("bwm_mini_e", 35.0, 0.14, 0.5, 0.15)
    build("think_city", 28.3, 0.16, 0.5, 0.15)
  }

  String initialize(Competition competition, List<String> completedInits) {
    // At competition startup
    if (!completedInits.find {'TariffMarket' == it}) {
      return null
    }
    if (!completedInits.find {'DefaultBroker' == it}) {
      return null
    }
    electricVehicleService.init()
    return 'ElectricVehicle'
  }

  /*
  Build EV instance
   */

  ElectricVehicle build(String name, BigDecimal capacity_kwh, BigDecimal avgConsumption_kwh, BigDecimal priceThreshold, BigDecimal socThreshold) {

    ElectricVehicle ev = new ElectricVehicle()

    // Create plugin config aka data that is publicly available
    PluginConfig config = new PluginConfig(roleName: 'ElectricVehicle', name: name,
        configuration: ['capacity_kwh': capacity_kwh.toString(),
            'avgConsumption_kwh': avgConsumption_kwh.toString(),
            'priceThreshold': priceThreshold.toString(),
            'socThreshold': socThreshold.toString()])

    config.save()
    ev.configure(config)

    if (!ev.validate()) {
      ev.errors.allErrors.each {log.error(it.toString())}
    }

    ev.save()

    return ev
  }
}
