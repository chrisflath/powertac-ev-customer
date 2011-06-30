package org.powertac.customer.ev

import org.powertac.common.interfaces.InitializationService
import org.powertac.common.Competition
import org.powertac.common.PluginConfig

class ElectricVehicleInitializationService implements InitializationService {

  static transactional = true

  def electricVehicleService

  void setDefaults() {
    // At simulator startup

    // Car-specific data
    def BMWData = [capacity_kwh: "35.0", requiredChargingHours: "3.0", batteryEfficiency : "0.93"]
    def thinkCityData = [capacity_kwh: "28.3", requiredChargingHours: "13.0", batteryEfficiency : "0.93"]

    // Create plugin config
    PluginConfig config = new PluginConfig(roleName: 'ElectricVehicle',
        configuration: BMWData).save()

    // Debug
    electricVehicleService.init()
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

  void setEvalulationFlatDefaultTariff() {

  }

  void setEvaluationHourlyRateTariff(){

  }
}
