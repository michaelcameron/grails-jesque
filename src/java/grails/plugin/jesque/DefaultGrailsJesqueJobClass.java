package grails.plugin.jesque;

import groovy.lang.Closure;
import org.codehaus.groovy.grails.commons.AbstractInjectableGrailsClass;
import org.codehaus.groovy.grails.commons.GrailsClassUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultGrailsJesqueJobClass extends AbstractInjectableGrailsClass implements GrailsJesqueJobClass {

    public static final String JOB = "Job";

    private Map triggers = new HashMap();

    public DefaultGrailsJesqueJobClass(Class clazz) {
        super(clazz, JOB);
        evaluateTriggers();
    }

    private void evaluateTriggers() {
        // registering additional triggersClosure from 'triggersClosure' closure if present
        Closure triggersClosure = (Closure) GrailsClassUtils.getStaticPropertyValue(getClazz(), "triggers");

        TriggersConfigBuilder builder = new TriggersConfigBuilder(this);

        if (triggersClosure != null) {
            builder.build(triggersClosure);
            triggers = (Map)builder.getTriggers();
        }
    }

    public Map getTriggers() {
        return triggers;
    }

    public String getQueue() {
        String queue = (String)getPropertyValue(GrailsJesqueJobClassProperty.QUEUE);
        if( queue == null )
            queue = GrailsJesqueJobClassProperty.DEFAULT_QUEUE;
        return queue;
    }

    public String getWorkerPool() {
        String workerPool = (String)getPropertyValue(GrailsJesqueJobClassProperty.WORKER_POOL);
        if( workerPool == null )
            workerPool = GrailsJesqueJobClassProperty.DEFAULT_WORKER_POOL;

        return workerPool;
    }

    public List getJobNames() {
        List jobNames = (List)getPropertyValue(GrailsJesqueJobClassProperty.JOB_NAMES);
        if( jobNames == null || jobNames.size() == 0 )
            jobNames = Arrays.asList(getClazz().getName(), getClazz().getSimpleName() );
        return jobNames;
    }
    
    public String toString() {
        return "Job > " + getName();
    }
}
