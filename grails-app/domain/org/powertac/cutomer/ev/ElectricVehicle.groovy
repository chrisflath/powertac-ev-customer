package org.powertac.cutomer.ev

import org.powertac.common.AbstractCustomer
import org.powertac.common.PluginConfig
import au.com.bytecode.opencsv.CSVReader

class ElectricVehicle extends AbstractCustomer {

  PluginConfig config

  static hasMany = [timeslots: ElectricVehicleTimeslot]

  static constraints = {
    config(nullable: false)
  }

  @Override
  void step() {
    super.step()
    log.error "some step here"
  }

  def loadData = {
    // Load generated driving profile
    CSVReader reader = new CSVReader(new FileReader("/Users/ddauer/Desktop/Profiles/Employee/Profile0.csv"));
    String[] nextLine;
    while ((nextLine = reader.readNext()) != null) {
      // Skip first line (header)
      if (nextLine[0] == "ID") { continue }

      System.out.println(nextLine[0] + nextLine[1] + "etc...");
    }


  }
}
