class PowertacEvCustomerGrailsPlugin {
  // the plugin version
  def version = "0.1"
  // the version or versions of Grails the plugin is designed for
  def grailsVersion = "1.3.7 > *"
  // the other plugins this plugin depends on
  def dependsOn = ['powertacCommon': '0.10 > *',
      'powertacServerInterface': '0.2 > *']
  // resources that are excluded from plugin packaging
  def pluginExcludes = [
      "grails-app/views/error.gsp"
  ]

  // TODO Fill in these fields
  def author = "David Dauer"
  def authorEmail = ""
  def title = "Electic Vehicle Customer Model for Power TAC"
  def description = '''\\
Electic Vehicle Customer Model for Power TAC
'''

  // URL to the plugin's documentation
  def documentation = "http://powertac.org/plugin/powertac-ev-customer"

  def doWithWebDescriptor = { xml ->
    // TODO Implement additions to web.xml (optional), this event occurs before
  }

  def doWithSpring = {
    // TODO Implement runtime spring config (optional)
  }

  def doWithDynamicMethods = { ctx ->
    // TODO Implement registering dynamic methods to classes (optional)
  }

  def doWithApplicationContext = { applicationContext ->
    // TODO Implement post initialization spring config (optional)
  }

  def onChange = { event ->
    // TODO Implement code that is executed when any artefact that this plugin is
    // watching is modified and reloaded. The event contains: event.source,
    // event.application, event.manager, event.ctx, and event.plugin.
  }

  def onConfigChange = { event ->
    // TODO Implement code that is executed when the project configuration changes.
    // The event is the same as for 'onChange'.
  }
}
