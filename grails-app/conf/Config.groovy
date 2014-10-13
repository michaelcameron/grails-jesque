import grails.plugin.jesque.test.SimpleJob
import grails.plugin.jesque.test.RedisAutoWireJob
import grails.plugin.jesque.test.ScheduledTestJob

grails.views.default.codec="none" // none, html, base64
grails.views.gsp.encoding="UTF-8"

// configuration for plugin testing - will not be included in the plugin zip
grails {
    redis {
        password = ''
    }
    jesque {
        pruneWorkersOnStartup = true
        workers {
            MyWorkerPool {
                queueNames = 'queueName'
                jobTypes = [
                        (SimpleJob.simpleName):SimpleJob,
                        (ScheduledTestJob.simpleName):ScheduledTestJob,
                ]
            }
            RedisWorkerPool {
                queueNames = 'redisAutoWireJobQueueName'
                jobTypes = [(RedisAutoWireJob.simpleName):RedisAutoWireJob]
            }
        }
    }
}

environment {
    test {
        grails {
            jesque {
                schedulerThreadActive = false
            }
        }
    }
}

log4j = {
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

    debug 'grails.plugin.jesque'
}
// Uncomment and edit the following lines to start using Grails encoding & escaping improvements

/* remove this line 
// GSP settings
grails {
    views {
        gsp {
            encoding = 'UTF-8'
            htmlcodec = 'xml' // use xml escaping instead of HTML4 escaping
            codecs {
                expression = 'html' // escapes values inside null
                scriptlet = 'none' // escapes output from scriptlets in GSPs
                taglib = 'none' // escapes output from taglibs
                staticparts = 'none' // escapes output from static template parts
            }
        }
        // escapes all not-encoded output at final stage of outputting
        filteringCodecForContentType {
            //'text/html' = 'html'
        }
    }
}
remove this line */
