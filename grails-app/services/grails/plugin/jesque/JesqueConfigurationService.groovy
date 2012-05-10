package grails.plugin.jesque

import org.joda.time.DateTimeZone

class JesqueConfigurationService {
    def grailsApplication
    def jesqueSchedulerService

    Boolean validateConfig(ConfigObject jesqueConfigMap) {
        jesqueConfigMap.workers.each{ workerPoolName, value ->
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

    void mergeClassConfigurationIntoConfigMap(ConfigObject jesqueConfigMap) {
        grailsApplication.jobClasses.each { GrailsJobClass jobArtefact ->
            def alreadyConfiguredPool = jesqueConfigMap.workers.find{ poolName, poolConfig ->
                poolConfig.jobTypes.any{ jobName, jobClassValue ->
                    jobClassValue == jobArtefact.clazz
                }
            }

            if( alreadyConfiguredPool ) {
                //already configured, make sure pool name matches, and queue is listed, otherwise error, do nothing else
                if( jobArtefact.workerPool != GrailsJobClassProperty.DEFAULT_WORKER_POOL && jobArtefact.workerPool != alreadyConfiguredPool.getKey() )
                    throw new Exception("Class ${jobArtefact.fullName} specifies worker pool ${jobArtefact.workerPool} but configuration file has ${alreadyConfiguredPool.getKey()}")

                if( alreadyConfiguredPool.value.queueNames instanceof String )
                    alreadyConfiguredPool.value.queueNames = [alreadyConfiguredPool.value.queueNames]

                if( jobArtefact.queue != GrailsJobClassProperty.DEFAULT_QUEUE && !(jobArtefact.queue in alreadyConfiguredPool.value.queueNames) )
                    throw new Exception("Class ${jobArtefact.fullName} specifies queue name ${jobArtefact.queue} but worker pool ${alreadyConfiguredPool.getKey()} has ${alreadyConfiguredPool.value.queueNames}")

                return
            }

            def workerPoolConfig = jesqueConfigMap.workers."${jobArtefact.workerPool}"
            if( !workerPoolConfig ) {
                workerPoolConfig.queueNames = []
                workerPoolConfig.jobTypes = [:]
            }

            if( workerPoolConfig.queueNames instanceof String )
                workerPoolConfig.queueNames = [workerPoolConfig.queueNames]

            jobArtefact.jobNames.each{ jobName ->
                workerPoolConfig.jobTypes += [(jobName):jobArtefact.clazz]
            }

            if( !(jobArtefact.queue in workerPoolConfig.queueNames) )
                workerPoolConfig.queueNames += jobArtefact.queue
        }
    }

    void scheduleJob(GrailsJobClass jobClass) {
        log.info("Scheduling ${jobClass.fullName}")

        jobClass.triggers.each {key, trigger ->
            String name = trigger.triggerAttributes[GrailsJobClassProperty.NAME]
            String cronExpression = trigger.triggerAttributes[GrailsJobClassProperty.CRON_EXPRESSION]
            DateTimeZone timeZone = DateTimeZone.forID(trigger.triggerAttributes[GrailsJobClassProperty.TIMEZONE])
            String queue = trigger.triggerAttributes[GrailsJobClassProperty.JESQUE_QUEUE]
            String jesqueJobName = trigger.triggerAttributes[GrailsJobClassProperty.JESQUE_JOB_NAME]
            List jesqueJobArguments = trigger.triggerAttributes[GrailsJobClassProperty.JESQUE_JOB_ARGUMENTS] ?: []

            jesqueSchedulerService.schedule(name, cronExpression, timeZone, queue, jesqueJobName, jesqueJobArguments)
        }
    }

    void deleteScheduleJob(GrailsJobClass jobClass) {
        log.info("Remove schedule for ${jobClass.fullName}")

        jobClass.triggers.each {key, trigger ->
            String name = trigger.triggerAttributes[GrailsJobClassProperty.NAME]

            jesqueSchedulerService.deleteSchedule(name)
        }
    }
}
