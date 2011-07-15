package grails.plugin.jesque

import grails.plugin.jesque.test.Foo

class DomainJob {

    void perform(id) {
        def foo = Foo.get(id)
        foo.name = foo.name + foo.name
        foo.save()
    }
}
