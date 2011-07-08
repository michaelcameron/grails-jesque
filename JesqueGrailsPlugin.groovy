import net.greghaines.jesque.client.ClientImpl
import net.greghaines.jesque.ConfigBuilder
import net.greghaines.jesque.client.ClientPoolImpl
import net.greghaines.jesque.Config

class JesqueGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.6 > *"
    // the other plugins this plugin depends on
    def dependsOn = [redis:"1.0.0M7 > *"]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    def author = "Michael Cameron"
    def authorEmail = "michael.e.cameron@gmail.com"
    def title = "Jesque"
    def description = '''\\
Grails Jesque plug-in
'''

    // URL to the plugin's documentation
    def documentation = "https://bitbucket.org/mcameron/grails-jesque"

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before 
    }

    def doWithSpring = {
        log.info "Creating jesque beans"
        def jesqueConfigMap = application.config?.grails?.jesque ?: [:]
        def jesqueConfigBuilder = new ConfigBuilder()
        if( jesqueConfigMap.namespace )
            jesqueConfigBuilder.withNamespace(jesqueConfigMap.namespace)

        def jesqueConfigInstance = jesqueConfigBuilder.build()

        jesqueConfig(Config, jesqueConfigInstance.host, jesqueConfigInstance.port, jesqueConfigInstance.timeout,
                        jesqueConfigInstance.password, jesqueConfigInstance.namespace, jesqueConfigInstance.database,
                        jesqueConfigInstance.jobPackage)
        jesqueClient(ClientPoolImpl, jesqueConfigInstance, ref('redisPool'))
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
