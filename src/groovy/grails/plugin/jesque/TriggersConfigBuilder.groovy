package grails.plugin.jesque

public class TriggersConfigBuilder extends BuilderSupport {
    private triggerNumber = 0
    private jobName

    def triggers = [:]

    public TriggersConfigBuilder(String jobName) {
        super()
        this.jobName = jobName
    }

    public build(closure) {
        closure.delegate = this
        closure.call()
        return triggers
    }

    protected void setParent(parent, child) {}

    protected createNode(name) {
        createNode(name, null, null)
    }

    protected createNode(name, value) {
        createNode(name, null, value)
    }

    protected createNode(name, Map attributes) {
        createNode(name, attributes, null)
    }

    protected Object createNode(name, Map attributes, Object value) {
        def trigger = createTrigger(name, attributes, value)
        triggers[trigger.triggerAttributes.name] = trigger
        trigger
    }

    private prepareCommonTriggerAttributes(HashMap triggerAttributes) {
        if(triggerAttributes[GrailsJobClassProperty.NAME] == null) triggerAttributes[GrailsJobClassProperty.NAME] = "${jobName}${triggerNumber++}"
        if(triggerAttributes[GrailsJobClassProperty.JESQUE_JOB_NAME] == null) {
            throw new Exception("Jesque Job Name Required");
        }
        if(triggerAttributes[GrailsJobClassProperty.JESQUE_QUEUE] == null) {
            throw new Exception("Jesque Queue Name Required");
        }
    }

    public Expando createTrigger(name, Map attributes, value) {
        def triggerAttributes = new HashMap(attributes)

        prepareCommonTriggerAttributes(triggerAttributes)

        def triggerType = name

        switch(triggerType) {
            case 'cron':
                prepareCronTriggerAttributes(triggerAttributes)
                break
            default:
                throw new Exception("Invalid format")
        }

        new Expando(triggerAttributes: triggerAttributes)
    }

    private def prepareCronTriggerAttributes(HashMap triggerAttributes) {
        if (!triggerAttributes?.cronExpression) throw new Exception("Cron trigger must have 'cronExpression' attribute")
        if (!CronExpression.isValidExpression(triggerAttributes[GrailsJobClassProperty.CRON_EXPRESSION].toString())) {
            throw new Exception("Cron expression '${triggerAttributes[GrailsJobClassProperty.CRON_EXPRESSION]}' in the job class ${jobName} is not a valid cron expression");
        }
    }

}
