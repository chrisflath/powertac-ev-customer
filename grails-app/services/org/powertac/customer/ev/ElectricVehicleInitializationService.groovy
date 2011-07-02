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
    def BMWData = [capacity_kwh: "35.0", requiredChargingHours: "3.0", batteryEfficiency: "0.93", avgConsumption: "0.14"]
    def thinkCityData = [capacity_kwh: "28.3", requiredChargingHours: "13.0", batteryEfficiency: "0.93", avgConsumption: "0.16"]

    // Create plugin config
    PluginConfig config = new PluginConfig(roleName: 'ElectricVehicle',
        configuration: thinkCityData).save()

    // For evaluation - will be called by initialize() when running competition.
    // electricVehicleService.init()
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
  Tariff used for evaluation purposes (List of EUR/kWh prices. List index == hour)
  The default broker could offer those within the simulation
   */
  def hourlyRateTariffRates() {
    [0.11, 0.12, 0.07, 0.08, 0.06, 0.1, 0.135, 0.17, 0.2, 0.15, 0.135, 0.15,
        0.22, 0.26, 0.21, 0.135, 0.12, 0.15, 0.215, 0.25, 0.18, 0.16, 0.15, 0.11]

    // flat tariff
    //[0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22,
    //    0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22, 0.22]
  }
}
