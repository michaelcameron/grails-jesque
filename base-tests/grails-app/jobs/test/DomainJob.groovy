package test

class DomainJob {

    void perform(name) {
        Foo foo = new Foo(name: name)
        foo.save(failOnError: true)
    }
}
