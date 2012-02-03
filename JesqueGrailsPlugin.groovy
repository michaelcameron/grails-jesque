import grails.plugin.jesque.GrailsJobClass
import grails.plugin.jesque.JobArtefactHandler
import net.greghaines.jesque.Config
import net.greghaines.jesque.ConfigBuilder
import net.greghaines.jesque.client.ClientPoolImpl
import net.greghaines.jesque.meta.dao.impl.FailureDAORedisImpl
import net.greghaines.jesque.meta.dao.impl.KeysDAORedisImpl
import net.greghaines.jesque.meta.dao.impl.QueueInfoDAORedisImpl
import net.greghaines.jesque.meta.dao.impl.WorkerInfoDAORedisImpl
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import grails.plugin.jesque.JesqueService
import grails.plugin.jesque.JesqueSchedulerThreadService
import grails.plugin.jesque.JesqueConfigurationService
import grails.util.GrailsUtil

class JesqueGrailsPlugin {

    def version = "0.3.0"
    def grailsVersion = "1.3.0 > *"
    def dependsOn = [redis: "1.0.0M7 > *"]
    def pluginExcludes = [
            "grails-app/views/**",
            "grails-app/domain/**",
            "grails-app/jobs/**",
            "test/**",
    ]

    def title = "Jesque - Redis backed job processing"
    def description = '''\\
Grails Jesque plug-in. Redis backed job processing
'''

    def author = "Michael Cameron"
    def authorEmail = "michael.e.cameron@gmail.com"

    def license = "APACHE"
    def developers = [
            [name: "Michael Cameron", email: "michael.e.cameron@gmail.com"],
            [name: "Ted Naleid", email: "contact@naleid.com"]]
    def documentation = "https://github.com/michaelcameron/grails-jesque"
    def scm = [url: "https://github.com/michaelcameron/grails-jesque"]

    def watchedResources = [
            "file:./grails-app/jobs/**/*Job.groovy",
            "file:./plugins/*/grails-app/jobs/**/*Job.groovy"
    ]

    def artefacts = [new JobArtefactHandler()]


    def doWithWebDescriptor = { xml ->
    }

    def doWithSpring = {
        log.info "Merging in default jesque config"
        loadJesqueConfig(application.config.grails.jesque)

        log.info "Creating jesque core beans"
        def redisConfigMap = application.config.grails.redis
        def jesqueConfigMap = application.config.grails.jesque

        def jesqueConfigBuilder = new ConfigBuilder()
        if(jesqueConfigMap.namespace)
            jesqueConfigBuilder = jesqueConfigBuilder.withNamespace(jesqueConfigMap.namespace)
        if(redisConfigMap.host)
            jesqueConfigBuilder = jesqueConfigBuilder.withHost(redisConfigMap.host)
        if(redisConfigMap.port)
            jesqueConfigBuilder = jesqueConfigBuilder.withPort(redisConfigMap.port as Integer)
        if(redisConfigMap.timeout)
            jesqueConfigBuilder = jesqueConfigBuilder.withTimeout(redisConfigMap.timeout as Integer)
        if(redisConfigMap.password)
            jesqueConfigBuilder = jesqueConfigBuilder.withPassword(redisConfigMap.password)

        def jesqueConfigInstance = jesqueConfigBuilder.build()

        jesqueConfig(Config, jesqueConfigInstance.host, jesqueConfigInstance.port, jesqueConfigInstance.timeout,
                     jesqueConfigInstance.password, jesqueConfigInstance.namespace, jesqueConfigInstance.database)
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

    def doWithDynamicMethods = { applicationContext ->
        //log.info "Create jesque async methods"
        //def jesqueService = applicationContext.jesqueService
        //jesqueService.createAsyncMethods()

        // if(application.serviceClasses) {
        //            application.serviceClasses.each { service ->
        //                service?.clazz?.methods?.each { Method method ->
        //                    if(method.getAnnotation(Async)) {
        //                        println "replacing ${method.name}"
        //                        def oldMethod = service.metaClass.getMetaMethod("${method.name}")
        //
        //                        service.metaClass."${method.name}AsyncJobMethod" = {->
        //                            println "in async method"
        //                            oldMethod.invoke(delegate, args)
        //                        }
        //
        //                        service.metaClass."${method.name}" = {->
        //                            def metaMethod = delegate.metaClass.getMetaMethod("${method.name}AsyncJobMethod", args)
        //                            metaMethod.invoke(delegate, args)
        //                        }
        //                    }
        //                }
        //            }
        //        }
    }

    def doWithApplicationContext = { applicationContext ->
        JesqueConfigurationService jesqueConfigurationService = applicationContext.jesqueConfigurationService

        log.info "Scheduling Jesque Jobs"
        application.jobClasses.each{ jobClass ->
            jesqueConfigurationService.scheduleJob(jobClass)
        }

        def jesqueConfigMap = application.config.grails.jesque

        if( jesqueConfigMap.schedulerThreadActive ) {
            log.info "Launching jesque scheduler thread"
            JesqueSchedulerThreadService jesqueSchedulerThreadService = applicationContext.jesqueSchedulerThreadService
            jesqueSchedulerThreadService.startSchedulerThread()
        }

        log.info "Starting jesque workers"
        JesqueService jesqueService = applicationContext.jesqueService

        jesqueConfigurationService.validateConfig(jesqueConfigMap)

        log.info "Found ${jesqueConfigMap.size()} workers"
        if(jesqueConfigMap.pruneWorkersOnStartup) {
            log.info "Pruning workers"
            jesqueService.pruneWorkers()
        }

        jesqueConfigurationService.mergeClassConfigurationIntoConfigMap(jesqueConfigMap)
        jesqueService.startWorkersFromConfig(jesqueConfigMap)
    }

    def onChange = { event ->
        //todo: manage changes
    }

    def onConfigChange = { event ->
        //todo: manage changes
    }

    private ConfigObject loadJesqueConfig(ConfigObject jesqueConfig) {
        GroovyClassLoader classLoader = new GroovyClassLoader(getClass().classLoader)

        // merging default jesque config into main application config
        def defaultConfig = new ConfigSlurper(GrailsUtil.environment).parse(classLoader.loadClass('DefaultJesqueConfig'))

        //may look weird, but we must merge the user config into default first so the user overrides default,
        // then merge back into the main to bring default values in that were not overridden
        def mergedConfig = defaultConfig.grails.jesque.merge(jesqueConfig)
        jesqueConfig.merge( mergedConfig )

        return jesqueConfig
    }
}
