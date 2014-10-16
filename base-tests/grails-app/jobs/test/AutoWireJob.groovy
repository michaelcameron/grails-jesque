package test

class AutoWireJob {

    def grailsApplication

    void perform() {
        assert grailsApplication != null
    }
}
