package org.powertac.customer.ev

import org.powertac.common.interfaces.InitializationService
import org.powertac.common.Competition
import org.powertac.common.PluginConfig

class ElectricVehicleInitializationService implements InitializationService {

  static transactional = true

  def electricVehicleService

  void setDefaults() {
    // At simulator startup
    // Create plugin config
    PluginConfig config = new PluginConfig(roleName: 'ElectricVehicle',
        configuration: [key: 'value', key2: 'value2']).save()
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
}
