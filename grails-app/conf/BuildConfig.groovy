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

        //wh custom repository
        def warmhealthResolver = new org.apache.ivy.plugins.resolver.URLResolver()
        ['libraries', 'builds'].each {
            warmhealthResolver.addArtifactPattern(
                    "http://repo.warmhealth.com/${it}/[organisation]/[module]/[revision]/grails-[artifact]-[revision].[ext]")
            warmhealthResolver.addIvyPattern(
                    "http://repo.warmhealth.com/${it}/[organisation]/[module]/[revision]/grails-[artifact]-[revision].[ext]")
        }
        warmhealthResolver.name = "my-repository"
        warmhealthResolver.settings = ivySettings
        resolver warmhealthResolver
    }
    dependencies {
        compile('net.greghaines:jesque:0.9.8',
                'redis.clients:jedis:2.0.0',
                'commons-pool:commons-pool:1.5.6',
                'org.codehaus.jackson:jackson-mapper-asl:1.8.3',
                'org.codehaus.jackson:jackson-core-asl:1.8.3') {
            transitive = false
        }

        test("org.seleniumhq.selenium:selenium-htmlunit-driver:2.3.1") {
            excludes "xercesImpl", "xmlParserAPIs", "xml-apis", "xerces", "commons-logging"
        }

        compile "org.codehaus.geb:geb-spock:0.6.0"

        plugins {
            compile(':redis:1.0.0.M7')
        }
    }
}