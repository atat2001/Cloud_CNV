package pt.ulisboa.tecnico.cnv.javassist.tools;

import javassist.CannotCompileException;
import javassist.CtBehavior;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;

public class ICount extends CodeDumper {
    private static final ThreadLocal<Long> nblocks  = ThreadLocal.withInitial(() -> 0L);
    private static final ThreadLocal<Long> nmethods = ThreadLocal.withInitial(() -> 0L);
    private static final ThreadLocal<Long> ninsts   = ThreadLocal.withInitial(() -> 0L);
    private static final ThreadLocal<Long> lastCpuTime = ThreadLocal.withInitial(() -> 0L);

    // CPU timer additions
    private static final ThreadLocal<Long> startCpuTime = new ThreadLocal<>();
    private static final ThreadMXBean cpuBean = ManagementFactory.getThreadMXBean();

    public ICount(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
        // Ensure CPU time tracking is enabled
        if (!cpuBean.isThreadCpuTimeEnabled()) {
            cpuBean.setThreadCpuTimeEnabled(true);
        }
    }

    public static void incBasicBlock(int position, int length) {
        nblocks.set(nblocks.get() + 1);
        ninsts.set(ninsts.get() + length);
    }

    public static void incBehavior(String name) {
        nmethods.set(nmethods.get() + 1);
    }

    // Reset counters and timers
    public static void reset() {
        nblocks.set(0L);
        nmethods.set(0L);
        ninsts.set(0L);
        startCpuTime.remove();
    }

    // Getters
    public static long getBlocks()  { return nblocks.get(); }
    public static long getMethods() { return nmethods.get(); }
    public static long getInsts()   { return ninsts.get(); }
    public static long getLastCpuTime() {
        return lastCpuTime.get();
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);

        // Start CPU timer
        behavior.insertBefore(ICount.class.getName() + ".startCpuTimer();");

        // Count the method invocation
        behavior.insertAfter(String.format(
                "%s.incBehavior(\"%s\");",
                ICount.class.getName(),
                behavior.getLongName()
        ));

        // End CPU timer and print
        /*
        behavior.insertAfter(String.format(
                "%s.endCpuTimer(\"%s\");",
                ICount.class.getName(),
                behavior.getLongName()
        ));
        */
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);

        block.behavior.insertAt(
                block.line,
                String.format("%s.incBasicBlock(%d, %d);",
                        ICount.class.getName(),
                        block.getPosition(),
                        block.getLength())
        );
    }

    // Thread-specific CPU timer logic
    public static void startCpuTimer() {
        startCpuTime.set(cpuBean.getCurrentThreadCpuTime());
    }

    public static void endCpuTimer(String methodName) {
        long end = cpuBean.getCurrentThreadCpuTime();
        Long start = startCpuTime.get();
        if (start != null) {
            long cpuTime = end - start;
            lastCpuTime.set(cpuTime);  // <- Store here
            System.out.println("[ICount] " + methodName + " method CPU time: " + cpuTime + " ns");
        }
        startCpuTime.remove();
    }
}
