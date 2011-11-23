package grails.plugin.jesque

enum TriggerState {
    Waiting('WAITING'),
    Acquired('ACQUIRED')

    String name

    TriggerState(String name) {
        this.name = name
    }
}
