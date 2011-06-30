package org.powertac.cutomer.ev

import org.powertac.common.AbstractCustomer
import org.powertac.common.PluginConfig
import au.com.bytecode.opencsv.CSVReader

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

  def loadData = {
    CSVReader reader = new CSVReader(new FileReader("/Users/ddauer/Desktop/Profiles/Employee/Profile0.csv"));
    String [] nextLine;
    while ((nextLine = reader.readNext()) != null) {
        // nextLine[] is an array of values from the line
        System.out.println(nextLine[0] + nextLine[1] + "etc...");
    }


  }
}
