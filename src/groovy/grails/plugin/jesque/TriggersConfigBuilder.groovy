package grails.plugin.jesque

import org.joda.time.DateTimeZone

public class TriggersConfigBuilder extends BuilderSupport {
    private Integer triggerNumber = 0
    private GrailsJobClass jobClass

    def triggers = [:]

    public TriggersConfigBuilder(GrailsJobClass jobClass) {
        super()
        this.jobClass = jobClass
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

    private prepareCommonTriggerAttributes(Map triggerAttributes) {
        if(triggerAttributes[GrailsJobClassProperty.NAME] == null)
            triggerAttributes[GrailsJobClassProperty.NAME] = "${jobClass.fullName}${triggerNumber++}"

        if(triggerAttributes[GrailsJobClassProperty.JESQUE_JOB_NAME] == null) {
            triggerAttributes[GrailsJobClassProperty.JESQUE_JOB_NAME] = jobClass.jobNames.first()
        }

        if(triggerAttributes[GrailsJobClassProperty.JESQUE_QUEUE] == null) {
            triggerAttributes[GrailsJobClassProperty.JESQUE_QUEUE] = jobClass.queue
        }

        if(triggerAttributes[GrailsJobClassProperty.JESQUE_JOB_ARGUMENTS] != null
            && !(triggerAttributes[GrailsJobClassProperty.JESQUE_JOB_ARGUMENTS] instanceof List))
            throw new Exception("If ${GrailsJobClassProperty.JESQUE_JOB_ARGUMENTS} exists, it must be a list");
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

    private def prepareCronTriggerAttributes(Map triggerAttributes) {
        if(!triggerAttributes[GrailsJobClassProperty.CRON_EXPRESSION])
            throw new Exception("Cron trigger must have 'cronExpression' attribute")

        if(!CronExpression.isValidExpression(triggerAttributes[GrailsJobClassProperty.CRON_EXPRESSION].toString()))
            throw new Exception("Cron expression '${triggerAttributes[GrailsJobClassProperty.CRON_EXPRESSION]}' in the job class ${jobClass.fullName} is not a valid cron expression");

        if(triggerAttributes[GrailsJobClassProperty.TIMEZONE]) {
            try {
                DateTimeZone.forID(triggerAttributes[GrailsJobClassProperty.TIMEZONE])
            } catch(Exception exception) {
                throw new Exception("Invalid ${GrailsJobClassProperty.TIMEZONE} on cron trigger", exception)
            }
        } else {
            triggerAttributes[GrailsJobClassProperty.TIMEZONE] = DateTimeZone.default.ID
        }
    }

}
