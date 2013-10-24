import grails.plugin.jesque.GrailsJesqueJobClass
import grails.plugin.jesque.JesqueJobArtefactHandler
import net.greghaines.jesque.Config
import net.greghaines.jesque.ConfigBuilder
import net.greghaines.jesque.admin.AdminClientImpl
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
import org.codehaus.groovy.grails.commons.spring.GrailsApplicationContext
import org.springframework.context.ApplicationContext
import grails.plugin.jesque.TriggersConfigBuilder
import grails.plugin.jesque.JesqueDelayedJobThreadService
import org.codehaus.groovy.grails.commons.GrailsApplication

class JesqueGrailsPlugin {

    def version = "0.7"
    def grailsVersion = "2.0.0 > *"
    def dependsOn = [redis: "1.3.2 > *"]
    def pluginExcludes = [
            "grails-app/views/**",
            "grails-app/domain/**",
            "grails-app/jobs/**",
            "test/**",
    ]

    def title = "Jesque - Redis backed job processing"
    def description = 'Grails Jesque plug-in. Redis backed job processing'

    def author = "Michael Cameron"
    def authorEmail = "michael.e.cameron@gmail.com"

    def license = "APACHE"
    def developers = [
            [name: "Michael Cameron", email: "michael.e.cameron@gmail.com"],
            [name: "Ted Naleid", email: "contact@naleid.com"],
            [name: "Philipp Eschenbach", email: "peh@kunstsysteme.com"]]
    def documentation = "https://github.com/michaelcameron/grails-jesque"
    def scm = [url: "https://github.com/michaelcameron/grails-jesque"]

    def loadAfter = ['core', 'hibernate']

    def watchedResources = [
            "file:./grails-app/jobs/**/*Job.groovy",
            "file:./plugins/*/grails-app/jobs/**/*Job.groovy"
    ]

    def artefacts = [new JesqueJobArtefactHandler()]

    def doWithWebDescriptor = { xml ->
    }

    def doWithSpring = {
        log.info "Merging in default jesque config"
        loadJesqueConfig(application.config.grails.jesque)

        if(!isJesqueEnabled(application)) {
            log.info "Jesque Disabled"
            return
        }

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
        if(redisConfigMap.database)
            jesqueConfigBuilder = jesqueConfigBuilder.withDatabase(redisConfigMap.database as Integer)

        def jesqueConfigInstance = jesqueConfigBuilder.build()

        jesqueAdminClient(AdminClientImpl, ref('jesqueConfig'))
        jesqueConfig(Config, jesqueConfigInstance.host, jesqueConfigInstance.port, jesqueConfigInstance.timeout,
                     jesqueConfigInstance.password, jesqueConfigInstance.namespace, jesqueConfigInstance.database)
        jesqueClient(ClientPoolImpl, jesqueConfigInstance, ref('redisPool'))

        failureDao(FailureDAORedisImpl, ref('jesqueConfig'), ref('redisPool'))
        keysDao(KeysDAORedisImpl, ref('jesqueConfig'), ref('redisPool'))
        queueInfoDao(QueueInfoDAORedisImpl, ref('jesqueConfig'), ref('redisPool'))
        workerInfoDao(WorkerInfoDAORedisImpl, ref('jesqueConfig'), ref('redisPool'))

        log.info "Creating jesque job beans"
        application.jesqueJobClasses.each {jobClass ->
            configureJobBeans.delegate = delegate
            configureJobBeans(jobClass)
        }
    }

    def configureJobBeans = {GrailsJesqueJobClass jobClass ->
        def fullName = jobClass.fullName

        "${fullName}Class"(MethodInvokingFactoryBean) {
            targetObject = ref("grailsApplication", true)
            targetMethod = "getArtefact"
            arguments = [JesqueJobArtefactHandler.TYPE, jobClass.fullName]
        }

        "${fullName}"(ref("${fullName}Class")) {bean ->
            bean.factoryMethod = "newInstance"
            bean.autowire = "byName"
            bean.scope = "prototype"
        }
    }

    def doWithDynamicMethods = { applicationContext ->
    }

    def doWithApplicationContext = { GrailsApplicationContext applicationContext ->
        if(!isJesqueEnabled(application))
            return

        TriggersConfigBuilder.metaClass.getGrailsApplication = { -> application }

        JesqueConfigurationService jesqueConfigurationService = applicationContext.jesqueConfigurationService

        log.info "Scheduling Jesque Jobs"
        application.jesqueJobClasses.each{ GrailsJesqueJobClass jobClass ->
            jesqueConfigurationService.scheduleJob(jobClass)
        }

        def jesqueConfigMap = application.config.grails.jesque

        if( jesqueConfigMap.schedulerThreadActive ) {
            log.info "Launching jesque scheduler thread"
            JesqueSchedulerThreadService jesqueSchedulerThreadService = applicationContext.jesqueSchedulerThreadService
            jesqueSchedulerThreadService.startSchedulerThread()
        }
        if( jesqueConfigMap.delayedJobThreadActive ) {
            log.info "Launching delayed job thread"
            JesqueDelayedJobThreadService jesqueDelayedJobThreadService = applicationContext.jesqueDelayedJobThreadService
            jesqueDelayedJobThreadService.startThread()
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
        if(jesqueConfigMap.createWorkersOnStartup) {
            log.info "Creating workers"
            jesqueService.startWorkersFromConfig(jesqueConfigMap)
        }

        applicationContext
    }

    def onChange = {event ->
        if(!isJesqueEnabled(application))
            return

        Class source = event.source
        if(!application.isArtefactOfType(JesqueJobArtefactHandler.TYPE, source)) {
            return
        }

        log.debug("Job ${source} changed. Reloading...")

        ApplicationContext context = event.ctx
        JesqueConfigurationService jesqueConfigurationService = context?.jesqueConfigurationService

        if(context && jesqueConfigurationService) {
            GrailsJesqueJobClass jobClass = application.getJobClass(source.name)
            if(jobClass)
                jesqueConfigurationService.deleteScheduleJob(jobClass)

            jobClass = (GrailsJesqueJobClass)application.addArtefact(JesqueJobArtefactHandler.TYPE, source)

            beans {
                configureJobBeans.delegate = delegate
                configureJobBeans(jobClass)
            }

            jesqueConfigurationService.scheduleJob(jobClass)
        } else {
            log.error("Application context or Jesque Scheduler not found. Can't reload Jesque plugin.")
        }
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

    private Boolean isJesqueEnabled(GrailsApplication application) {
        def jesqueConfigMap = application.config.grails.jesque

        Boolean isJesqueEnabled = true

        def enabled = jesqueConfigMap.enabled
        if (enabled != null) {
            if (enabled instanceof String) {
                isJesqueEnabled = Boolean.parseBoolean(enabled)
            } else if (enabled instanceof Boolean) {
                isJesqueEnabled = enabled
            }
        }

        return isJesqueEnabled
    }
}
