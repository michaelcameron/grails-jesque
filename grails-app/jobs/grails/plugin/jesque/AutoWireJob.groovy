package grails.plugin.jesque

class AutoWireJob {

    def grailsApplication

    void perform() {
        assert grailsApplication != null
    }
}
