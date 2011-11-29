package grails.plugin.jesque

import org.codehaus.groovy.grails.commons.GrailsClassUtils

class JesqueConfigurationService {
    def grailsApplication

    Boolean validateConfig(Map jesqueConfigMap) {
        jesqueConfigMap?.workers?.each{ workerPoolName, value ->
            if( value.workers && !(value.workers instanceof Integer)  )
                throw new Exception("Invalid worker count ${value.workers} for pool $workerPoolName")

            def queueNames = value.queueNames
            if( !((queueNames instanceof String) || (queueNames instanceof List<String>)) )
                throw new Exception("Invalid queueNames ($queueNames) for pool $workerPoolName, must be a String or a List<String>")

            def jobTypes = value.jobTypes
            if( !(jobTypes instanceof Map) )
                throw new Exception("Invalid jobTypes ($jobTypes) for pool $workerPoolName, must be a map")

            jobTypes.each{ jobName, jobClass ->
                if( !(jobClass instanceof Class) )
                    throw new Exception("Invalid jobClass for jobName ($jobName) for pool $workerPoolName, the value must be a Class")
            }
        }

        return true
    }

    void mergeClassConfigurationIntoConfigMap(Map jesqueConfigMap) {
        grailsApplication.jobClasses.each { jobArtefact ->
            Class jobClass = jobArtefact.clazz
            def alreadyConfiguredPool = jesqueConfigMap.workers.find{ poolName, poolConfig ->
                poolConfig.jobTypes.any{ jobName, jobClassValue ->
                    jobClassValue == jobClass
                }
            }

            def queue = GrailsClassUtils.isStaticProperty(jobClass, GrailsJobClassProperty.QUEUE) ? jobClass[GrailsJobClassProperty.QUEUE] : GrailsJobClassProperty.DEFAULT_QUEUE
            def workerPool = GrailsClassUtils.isStaticProperty(jobClass, GrailsJobClassProperty.WORKER_POOL) ? jobClass[GrailsJobClassProperty.WORKER_POOL] : GrailsJobClassProperty.DEFAULT_WORKER_POOL
            def jobNames = GrailsClassUtils.isStaticProperty(jobClass, GrailsJobClassProperty.JOB_NAMES) ? jobClass[GrailsJobClassProperty.JOB_NAMES] : [jobClass.simpleName, jobClass.name]

            if( alreadyConfiguredPool ) {
                //already configured, make sure pool name matches, and queue is listed, otherwise error, do nothing else
                if( workerPool != GrailsJobClassProperty.DEFAULT_WORKER_POOL && workerPool != alreadyConfiguredPool.getKey() ) {
                    throw new Exception("Class ${jobClass.name} specifies worker pool ${workerPool} but configuration file has ${alreadyConfiguredPool.getKey()}")
                }

                if( alreadyConfiguredPool.value.queueNames instanceof String )
                    alreadyConfiguredPool.value.queueNames = [alreadyConfiguredPool.value.queueNames]
                if( queue != GrailsJobClassProperty.DEFAULT_QUEUE && !(queue in alreadyConfiguredPool.value.queueNames) ) {
                    throw new Exception("Class ${jobClass.name} specifies queue name ${queue} but worker pool ${alreadyConfiguredPool.getKey()} has ${alreadyConfiguredPool.value.queueNames}")
                }
                return
            }

            def workerPoolConfig = jesqueConfigMap.workers[workerPool]
            if( !workerPoolConfig ) {
                workerPoolConfig.queueNames = []
                workerPoolConfig.jobTypes = [:]
            }

            if( workerPoolConfig.queueNames instanceof String )
                workerPoolConfig.queueNames = [workerPoolConfig.queueNames]

            jobNames.each{ jobName ->
                workerPoolConfig.jobTypes += [(jobName):jobClass]
            }

            if( !(queue in workerPoolConfig.queueNames) )
                workerPoolConfig.queueNames += queue
        }
    }
}
