package org.cytoscape.serviceapps.run;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Threads that don't keep Desktop from exiting, numbered under one name. */
public final class DaemonThreads {

    private DaemonThreads() {}

    /** Makes threads named {@code <prefix>-1}, {@code <prefix>-2} and so on. */
    public static ThreadFactory named(String prefix) {
        AtomicInteger count = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
