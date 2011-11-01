import grails.plugin.jesque.test.SimpleJob
import grails.plugin.jesque.test.RedisAutoWireJob

// configuration for plugin testing - will not be included in the plugin zip
grails {
    jesque {
        pruneWorkersOnStartup = true
        workers {
            MyWorkerPool {
                queueNames = 'queueName'
                jobTypes = [(SimpleJob.simpleName):SimpleJob]
            }
            RedisWorkerPool {
                queueNames = 'redisAutoWireJobQueueName'
                jobTypes = [(RedisAutoWireJob.simpleName):RedisAutoWireJob]
            }
        }
    }
}

log4j = {
    // Example of changing the log pattern for the default console
    // appender:
    //
    appenders {
        console name:'stdout', layout:pattern(conversionPattern: '%d{HH:mm:ss} [%5p] %-30.30c{2} %m%n')
    }

    root {
        info('stdout')
    }

    error  'org.codehaus.groovy.grails.web.servlet',  //  controllers
           'org.codehaus.groovy.grails.web.pages', //  GSP
           'org.codehaus.groovy.grails.web.sitemesh', //  layouts
           'org.codehaus.groovy.grails.web.mapping.filter', // URL mapping
           'org.codehaus.groovy.grails.web.mapping', // URL mapping
           'org.codehaus.groovy.grails.commons', // core / classloading
           'org.codehaus.groovy.grails.plugins', // plugins
           'org.codehaus.groovy.grails.orm.hibernate', // hibernate integration
           'org.springframework',
           'org.hibernate',
           'net.sf.ehcache.hibernate'

    warn   'org.mortbay.log'
}
