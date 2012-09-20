grails{
    jesque {
        pruneWorkersOnStartup = true
        createWorkersOnStartup = true
        schedulerThreadActive = true
    }
}

environments {
    test {
        grails{
            jesque {
                pruneWorkersOnStartup = false
                schedulerThreadActive = false
            }
        }
    }
}