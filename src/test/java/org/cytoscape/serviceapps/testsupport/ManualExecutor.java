package org.cytoscape.serviceapps.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/** An executor that holds tasks until the test runs them, so background work is deterministic. */
public class ManualExecutor extends AbstractExecutorService {

    private final List<Runnable> queued = new ArrayList<>();
    private boolean shutdown;

    @Override
    public synchronized void execute(Runnable command) {
        if (shutdown) {
            throw new java.util.concurrent.RejectedExecutionException("shut down");
        }
        queued.add(command);
    }

    /** Runs everything queued so far, and anything those tasks queue. */
    public void runAll() {
        while (true) {
            List<Runnable> batch;
            synchronized (this) {
                if (queued.isEmpty()) {
                    return;
                }
                batch = new ArrayList<>(queued);
                queued.clear();
            }
            batch.forEach(Runnable::run);
        }
    }

    public synchronized int pending() {
        return queued.size();
    }

    @Override
    public synchronized void shutdown() {
        shutdown = true;
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        shutdown = true;
        List<Runnable> dropped = new ArrayList<>(queued);
        queued.clear();
        return dropped;
    }

    @Override
    public synchronized boolean isShutdown() {
        return shutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return shutdown && queued.isEmpty();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return isTerminated();
    }
}
