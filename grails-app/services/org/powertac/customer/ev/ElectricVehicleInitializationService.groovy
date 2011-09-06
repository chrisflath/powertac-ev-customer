package org.powertac.customer.ev

import org.powertac.common.interfaces.InitializationService
import org.powertac.common.Competition
import org.powertac.common.PluginConfig
import org.powertac.cutomer.ev.ElectricVehicle
import au.com.bytecode.opencsv.CSVReader
import org.powertac.cutomer.ev.ElectricVehicleProfiles

class ElectricVehicleInitializationService implements InitializationService {

  static transactional = true

  def electricVehicleService

  void setDefaults() {
    // At simulator startup

//    // load profiles from /powertac-server/rawProfiles.csv
//    def baseDir = System.properties.getProperty('base.dir')
//    def csvFile = "${baseDir}/rawProfiles.csv"
//    CSVReader reader = new CSVReader(new FileReader(csvFile))
//
//    // save profiles
//    List<String[]> profiles = reader.readAll()
//    ElectricVehicleProfiles profileStore = new ElectricVehicleProfiles(profiles: profiles)
//    if (!profileStore.validate()) {
//      profileStore.errors.allErrors.each {log.error(it.toString())}
//    }
//    profileStore.save()
//
//    log.error "saved ${profileStore}"

    // create evs
    for ( i in 0..19 )
    {
        BigDecimal priceThreshold = Math.random()*0.1+0.08
        BigDecimal socThreshold = Math.random()*0.2 + 0.4
        if (i%2==0)
        {
            build(i, "bwm_mini_e ${i}", 35.0, 0.14, priceThreshold, 16.0*socThreshold, 5.5)
        }
        else
        {
            build(i, "think_city ${i}", 28.3, 0.16, priceThreshold, 15.0*socThreshold, 5.5)
        }
    }
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

  ElectricVehicle build(Integer profileId, String name, BigDecimal capacity_kwh, BigDecimal avgConsumption_kwh, BigDecimal priceThreshold, BigDecimal socThreshold, BigDecimal maxChargingSpeedPerHour) {

    ElectricVehicle ev = new ElectricVehicle()

    // Create plugin config aka data that is publicly available
    PluginConfig config = new PluginConfig(roleName: 'ElectricVehicle', name: name,
        configuration: ['profileId': profileId.toString(),
            'capacity_kwh': capacity_kwh.toString(),
            'avgConsumption_kwh': avgConsumption_kwh.toString(),
            'priceThreshold': priceThreshold.toString(),
            'socThreshold': socThreshold.toString(),
            'maxChargingSpeedPerHour': maxChargingSpeedPerHour.toString()])

    config.save()
    ev.configure(config)

    if (!ev.validate()) {
      ev.errors.allErrors.each {log.error(it.toString())}
    }

    ev.save()

    return ev
  }
}
