grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
//grails.project.war.file = "target/${appName}-${appVersion}.war"
grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // uncomment to disable ehcache
        // excludes 'ehcache'
    }
    log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    repositories {
        grailsPlugins()
        grailsHome()
        grailsCentral()

        mavenLocal()
        mavenCentral()
        mavenRepo "http://snapshots.repository.codehaus.org"
        mavenRepo "http://repository.codehaus.org"
        mavenRepo "http://download.java.net/maven/2/"
        mavenRepo "http://repository.jboss.com/maven2/"
    }
    dependencies {
        compile('commons-pool:commons-pool:1.6') {
            transitive = false
        }

        compile('net.greghaines:jesque:1.2.0')
        compile('redis.clients:jedis:2.1.0')

        compile('joda-time:joda-time:2.0')

        test("org.seleniumhq.selenium:selenium-htmlunit-driver:2.3.1") {
            excludes "xercesImpl", "xmlParserAPIs", "xml-apis", "xerces", "commons-logging"
            export = false
        }
    }
    plugins {
        compile(':redis:1.3.1')
        compile(':release:2.0.0', ':rest-client-builder:1.0.2') {
            export = false
        }
        test(":spock:0.6") {
            export = false
        }
        test(":hibernate:$grailsVersion") {
            export = false
        }
    }
}
