import net.greghaines.jesque.ConfigBuilder
import net.greghaines.jesque.client.ClientPoolImpl
import net.greghaines.jesque.Config
import net.greghaines.jesque.meta.dao.impl.FailureDAORedisImpl
import net.greghaines.jesque.meta.dao.impl.KeysDAORedisImpl
import net.greghaines.jesque.meta.dao.impl.QueueInfoDAORedisImpl
import net.greghaines.jesque.meta.dao.impl.WorkerInfoDAORedisImpl
import grails.plugin.jesque.JobArtefactHandler
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import grails.plugin.jesque.GrailsJobClass

class JesqueGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.0 > *"
    // the other plugins this plugin depends on
    def dependsOn = [redis:"1.0.0M7 > *", hibernate:"1.3.6 > *"]
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

    def watchedResources = [
            "file:./grails-app/jobs/**/*Job.groovy",
            "file:./plugins/*/grails-app/jobs/**/*Job.groovy"
    ]

    def artefacts = [new JobArtefactHandler()]

    def documentation = "https://github.com/michaelcameron/grails-jesque"

    def doWithWebDescriptor = { xml ->
    }

    def doWithSpring = {
        log.info "Creating jesque core beans"
        def jesqueConfigMap = application.config?.grails?.jesque ?: [:]
        def jesqueConfigBuilder = new ConfigBuilder()
        if( jesqueConfigMap.namespace )
            jesqueConfigBuilder.withNamespace(jesqueConfigMap.namespace)

        def jesqueConfigInstance = jesqueConfigBuilder.build()

        jesqueConfig(Config, jesqueConfigInstance.host, jesqueConfigInstance.port, jesqueConfigInstance.timeout,
                        jesqueConfigInstance.password, jesqueConfigInstance.namespace, jesqueConfigInstance.database,
                        jesqueConfigInstance.jobPackage)
        jesqueClient(ClientPoolImpl, jesqueConfigInstance, ref('redisPool'))

        failureDao(FailureDAORedisImpl, ref('jesqueConfig'), ref('redisPool'))
        keysDao(KeysDAORedisImpl, ref('jesqueConfig'), ref('redisPool'))
        queueInfoDao(QueueInfoDAORedisImpl, ref('jesqueConfig'), ref('redisPool'))
        workerInfoDao(WorkerInfoDAORedisImpl, ref('jesqueConfig'), ref('redisPool'))

        log.info "Creating jesque job beans"
        application.jobClasses.each {jobClass ->
            configureJobBeans.delegate = delegate
            configureJobBeans(jobClass)
        }


    }

    def configureJobBeans = {GrailsJobClass jobClass ->
        def fullName = jobClass.fullName

        "${fullName}Class"(MethodInvokingFactoryBean) {
            targetObject = ref("grailsApplication", true)
            targetMethod = "getArtefact"
            arguments = [JobArtefactHandler.TYPE, jobClass.fullName]
        }

        "${fullName}"(ref("${fullName}Class")) {bean ->
            bean.factoryMethod = "newInstance"
            bean.autowire = "byName"
            bean.scope = "prototype"
        }
    }

    def doWithDynamicMethods = { ctx ->
    }

    def doWithApplicationContext = { applicationContext ->
        log.info "Starting jesque workers"
        def jesqueService = applicationContext.jesqueService
        def jesqueConfigMap = application.config?.grails?.jesque ?: [:]
        //todo:merge in a default config
        if( !jesqueConfigMap?.containsKey('pruneWorkersOnStartup') || jesqueConfigMap?.prunerWorkersOnStartup )
            jesqueService.pruneWorkers()
        jesqueService.startWorkersFromConfig()
    }

    def onChange = { event ->
        //todo: manage changes
    }

    def onConfigChange = { event ->
        //todo: manage changes
    }
}
