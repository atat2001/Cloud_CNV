package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.List;
import javassist.CtBehavior;
import javassist.CtClass;

public class MethodExecutionTimer extends CodeDumper {

    public MethodExecutionTimer(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);

        // Declare the bean and CPU time variables
        behavior.addLocalVariable("startCpuTime", CtClass.longType);
        behavior.addLocalVariable("endCpuTime", CtClass.longType);
        behavior.addLocalVariable("cpuTime", CtClass.longType);

        // Insert code to get thread-specific CPU time before method execution
        behavior.insertBefore(
                "startCpuTime = java.lang.management.ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();"
        );

        // Insert code to get CPU time after method execution and print result
        StringBuilder builder = new StringBuilder();
        builder.append("endCpuTime = java.lang.management.ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();");
        builder.append("cpuTime = endCpuTime - startCpuTime;");
        builder.append(String.format(
                "System.out.println(\"[%s] %s method CPU time: \" + cpuTime + \" ns!\");",
                this.getClass().getSimpleName(),
                behavior.getLongName()
        ));

        behavior.insertAfter(builder.toString());
    }
}
