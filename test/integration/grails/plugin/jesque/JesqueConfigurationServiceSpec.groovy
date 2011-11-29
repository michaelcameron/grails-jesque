package grails.plugin.jesque

import grails.plugin.spock.IntegrationSpec
import grails.plugin.jesque.test.SelfConfiguredJob
import grails.plugin.jesque.test.SimpleJob

class JesqueConfigurationServiceSpec extends IntegrationSpec{

    def jesqueConfigurationService

    void "test mergeClassConfigurationIntoConfigMap worker pool mismatch"() {
        given:
        def config = new ConfigSlurper().parse("""
            import grails.plugin.jesque.test.SelfConfiguredJob

            grails {
                jesque {
                    workers {
                        SomeOtherWorkerPool {
                            queueNames = 'MyQueue'
                            jobTypes = [
                                    (SelfConfiguredJob.simpleName):SelfConfiguredJob
                            ]
                        }
                    }
                }
            }
        """)

        when:
        jesqueConfigurationService.mergeClassConfigurationIntoConfigMap(config.grails.jesque)

        then:
        Exception exception = thrown()
        while( exception.cause ) { exception = exception.cause }
        exception.message =~ /specifies worker pool .* but configuration file has/
    }

    void "test mergeClassConfigurationIntoConfigMap queue mismatch"() {
        given:
        def config = new ConfigSlurper().parse("""
            import grails.plugin.jesque.test.SelfConfiguredJob

            grails {
                jesque {
                    workers {
                        MyWorkerPool {
                            queueNames = 'SomeOtherQueueName'
                            jobTypes = [
                                    (SelfConfiguredJob.simpleName):SelfConfiguredJob,
                            ]
                        }
                    }
                }
            }
        """)

        when:
        jesqueConfigurationService.mergeClassConfigurationIntoConfigMap(config.grails.jesque)

        then:
        Exception exception = thrown()
        while( exception.cause ) { exception = exception.cause }
        exception.message =~ /specifies queue name .* but worker pool .* has/
    }

    void "test mergeClassConfigurationIntoConfigMap with specified values"() {
        given:
        def config = new ConfigSlurper().parse("")

        when:
        jesqueConfigurationService.mergeClassConfigurationIntoConfigMap(config.grails.jesque)

        then:
        noExceptionThrown()
        config.grails.jesque.workers.MyWorkerPool
        config.grails.jesque.workers.MyWorkerPool.queueNames == [SelfConfiguredJob.queue]
        SelfConfiguredJob.jobNames.every {
            config.grails.jesque.workers.MyWorkerPool.jobTypes.containsKey(it)
        }
    }

    void "test mergeClassConfigurationIntoConfigMap with specified defaults"() {
        given:
        def config = new ConfigSlurper().parse("")

        when:
        jesqueConfigurationService.mergeClassConfigurationIntoConfigMap(config.grails.jesque)

        then:
        noExceptionThrown()
        config.grails.jesque.workers[GrailsJobClassProperty.DEFAULT_WORKER_POOL]
        config.grails.jesque.workers[GrailsJobClassProperty.DEFAULT_WORKER_POOL].queueNames == [GrailsJobClassProperty.DEFAULT_QUEUE]
        config.grails.jesque.workers[GrailsJobClassProperty.DEFAULT_WORKER_POOL].jobTypes.containsKey(SimpleJob.canonicalName)
        config.grails.jesque.workers[GrailsJobClassProperty.DEFAULT_WORKER_POOL].jobTypes.containsKey(SimpleJob.name)
    }
}
