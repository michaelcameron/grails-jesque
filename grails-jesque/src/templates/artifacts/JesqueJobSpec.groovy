@artifact.package@

import net.greghaines.jesque.meta.dao.QueueInfoDAO
import net.greghaines.jesque.meta.dao.FailureDAO
import grails.plugin.spock.IntegrationSpec

class @artifact.name@ extends IntegrationSpec {

    def jesqueService
    QueueInfoDAO queueInfoDao
    FailureDAO failureDao

    def "test @artifact.name@ simple functionality"() {
        given:
        def queueName = "@artifact.name@Queue"

        when:
        // TODO: add when

        then:
        true
    }
}