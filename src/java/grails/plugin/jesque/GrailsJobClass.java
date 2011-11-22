package grails.plugin.jesque;

import org.codehaus.groovy.grails.commons.InjectableGrailsClass;
import java.util.Map;

public interface GrailsJobClass extends InjectableGrailsClass {

    public Map getTriggers();
}
