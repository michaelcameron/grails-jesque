package grails.plugin.jesque

import org.joda.time.DateTimeZone

public class TriggersConfigBuilder extends BuilderSupport {
    private Integer triggerNumber = 0
    private GrailsJesqueJobClass jobClass

    def triggers = [:]

    public TriggersConfigBuilder(GrailsJesqueJobClass jobClass) {
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
        if(triggerAttributes[GrailsJesqueJobClassProperty.NAME] == null)
            triggerAttributes[GrailsJesqueJobClassProperty.NAME] = "${jobClass.fullName}${triggerNumber++}"

        if(triggerAttributes[GrailsJesqueJobClassProperty.JESQUE_JOB_NAME] == null) {
            triggerAttributes[GrailsJesqueJobClassProperty.JESQUE_JOB_NAME] = jobClass.jobNames.first()
        }

        if(triggerAttributes[GrailsJesqueJobClassProperty.JESQUE_QUEUE] == null) {
            triggerAttributes[GrailsJesqueJobClassProperty.JESQUE_QUEUE] = jobClass.queue
        }

        if(triggerAttributes[GrailsJesqueJobClassProperty.JESQUE_JOB_ARGUMENTS] != null
            && !(triggerAttributes[GrailsJesqueJobClassProperty.JESQUE_JOB_ARGUMENTS] instanceof List))
            throw new Exception("If ${GrailsJesqueJobClassProperty.JESQUE_JOB_ARGUMENTS} exists, it must be a list");
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
        if(!triggerAttributes[GrailsJesqueJobClassProperty.CRON_EXPRESSION])
            throw new Exception("Cron trigger must have 'cronExpression' attribute")

        if(!CronExpression.isValidExpression(triggerAttributes[GrailsJesqueJobClassProperty.CRON_EXPRESSION].toString()))
            throw new Exception("Cron expression '${triggerAttributes[GrailsJesqueJobClassProperty.CRON_EXPRESSION]}' in the job class ${jobClass.fullName} is not a valid cron expression");

        if(triggerAttributes[GrailsJesqueJobClassProperty.TIMEZONE]) {
            try {
                DateTimeZone.forID(triggerAttributes[GrailsJesqueJobClassProperty.TIMEZONE])
            } catch(Exception exception) {
                throw new Exception("Invalid ${GrailsJesqueJobClassProperty.TIMEZONE} on cron trigger", exception)
            }
        } else {
            triggerAttributes[GrailsJesqueJobClassProperty.TIMEZONE] = DateTimeZone.default.ID
        }
    }

}
