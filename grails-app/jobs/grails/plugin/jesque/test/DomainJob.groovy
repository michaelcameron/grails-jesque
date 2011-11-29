package grails.plugin.jesque.test

class DomainJob {

    void perform(id) {
        def foo = Foo.get(id)
        foo.name = foo.name + foo.name
        foo.save()
    }
}
