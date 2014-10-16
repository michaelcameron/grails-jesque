grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
//grails.project.war.file = "target/${appName}-${appVersion}.war"
grails.project.dependency.resolver = "ivy" // or ivy
grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // uncomment to disable ehcache
        excludes 'ant'
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
        mavenRepo "https://oss.sonatype.org/content/repositories/snapshots/"
        mavenRepo "http://repo.grails.org/grails/libs-releases/"
        mavenRepo "http://m2repo.spockframework.org/ext/"
        mavenRepo "http://m2repo.spockframework.org/snapshots/"
    }
    dependencies {
        compile('commons-pool:commons-pool:1.6')

        compile('net.greghaines:jesque:1.3.2')
        compile('redis.clients:jedis:2.6.0')
        compile('joda-time:joda-time:2.5')
    }
    plugins {
        compile ":redis:1.5.5"
        build(':release:3.0.1', ':rest-client-builder:2.0.3') {
            export = false
        }
    }
}
