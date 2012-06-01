package grails.plugin.jesque;

import org.codehaus.groovy.grails.commons.InjectableGrailsClass;

import java.util.List;
import java.util.Map;

public interface GrailsJesqueJobClass extends InjectableGrailsClass {

    public Map getTriggers();
    
    public String getQueue();

    public String getWorkerPool();

    public List getJobNames();
}
