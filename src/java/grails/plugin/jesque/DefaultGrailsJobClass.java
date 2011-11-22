package grails.plugin.jesque;

import groovy.lang.Closure;
import org.codehaus.groovy.grails.commons.AbstractInjectableGrailsClass;
import org.codehaus.groovy.grails.commons.GrailsClassUtils;

import java.util.HashMap;
import java.util.Map;

public class DefaultGrailsJobClass extends AbstractInjectableGrailsClass implements GrailsJobClass {

    public static final String JOB = "Job";

    private Map triggers = new HashMap();

    public DefaultGrailsJobClass(Class clazz) {
        super(clazz, JOB);
        evaluateTriggers();
    }

    private void evaluateTriggers() {
        // registering additional triggersClosure from 'triggersClosure' closure if present
        Closure triggersClosure = (Closure) GrailsClassUtils.getStaticPropertyValue(getClazz(), "triggers");

        TriggersConfigBuilder builder = new TriggersConfigBuilder(getFullName());

        if (triggersClosure != null) {
            builder.build(triggersClosure);
            triggers = (Map) builder.getTriggers();
        }
    }

    public Map getTriggers() {
        return triggers;
    }
}
