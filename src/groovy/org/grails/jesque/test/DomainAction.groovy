package org.grails.jesque.test

class DomainAction implements Runnable {
    def id

    DomainAction(id) {
        this.id = id
    }

    void run() {
        def foo = Foo.get(id)
        foo.name = foo.name + foo.name
        foo.save()
    }
}
