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
        compile('net.greghaines:jesque:0.9.6',
                'redis.clients:jedis:2.0.0',
                'commons-pool:commons-pool:1.5.6',
                'org.codehaus.jackson:jackson-mapper-asl:1.8.2',
                'org.codehaus.jackson:jackson-core-asl:1.8.2',
                'org.slf4j:slf4j-api:1.6.1') {
            transitive = false
        }

        plugins {
            test(':build-test-data:1.1.1') {
                
            }
        }
    }
}

grails.plugin.location.redis = "../grails-redis"