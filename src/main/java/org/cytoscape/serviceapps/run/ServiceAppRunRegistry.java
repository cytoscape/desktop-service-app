package org.cytoscape.serviceapps.run;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cytoscape.serviceapps.model.TaskStatus;

/**
 * Background runs, for {@code serviceapps task call} (cytoscape/cytoscape#45).
 *
 * <p>Issues a Desktop task ID for each run, runs it on its own thread with a {@link
 * RecordingTaskMonitor}, and keeps its latest state in the spec's Task Status model. {@code
 * complete} is reported only after the result is applied. The last 100 runs are kept.
 */
public class ServiceAppRunRegistry {

    public static final int KEPT_RUNS = 100;

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceAppRunRegistry.class);

    private final ActiveRuns active;
    private final ExecutorService executor;
    private final Map<String, TaskStatus> states =
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, TaskStatus> eldest) {
                    return size() > KEPT_RUNS;
                }
            };

    public ServiceAppRunRegistry(ActiveRuns active) {
        this.active = active;
        AtomicInteger count = new AtomicInteger();
        this.executor =
                Executors.newCachedThreadPool(
                        runnable -> {
                            Thread thread =
                                    new Thread(
                                            runnable, "service-app-run-" + count.incrementAndGet());
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    /**
     * Starts a run in the background.
     *
     * @param runFor makes the run, given the listener that records its state
     * @return the run's Desktop task ID
     */
    public String start(Function<RunListener, ServiceAppRun> runFor) {
        String id = UUID.randomUUID().toString();
        record(id, TaskStatus.SUBMITTED, 0, "Submitting");
        ServiceAppRun run =
                runFor.apply((status, progress, message) -> record(id, status, progress, message));
        executor.execute(
                () -> {
                    try {
                        run.run(new RecordingTaskMonitor());
                    } catch (RuntimeException e) {
                        LOGGER.info("Background run {} failed: {}", id, e.getMessage());
                        record(
                                id,
                                TaskStatus.FAILED,
                                current(id).map(s -> s.progress).orElse(0.0),
                                e.getMessage());
                    }
                });
        return id;
    }

    /** The run's latest state, with {@code result} null, or empty for an unknown or expired ID. */
    public synchronized Optional<TaskStatus> status(String id) {
        return current(id).map(ServiceAppRunRegistry::copy);
    }

    public ActiveRuns active() {
        return active;
    }

    /** Cancels every run and stops the background threads. */
    public void shutdown() {
        active.cancelAll();
        executor.shutdownNow();
    }

    private synchronized Optional<TaskStatus> current(String id) {
        return Optional.ofNullable(states.get(id));
    }

    private synchronized void record(String id, String status, double progress, String message) {
        TaskStatus previous = states.get(id);
        if (previous != null
                && (TaskStatus.COMPLETE.equals(previous.status)
                        || TaskStatus.FAILED.equals(previous.status))) {
            return; // a finished run's state is final
        }
        TaskStatus state = new TaskStatus();
        state.id = id;
        state.status = status;
        state.progress = progress;
        state.message = message == null ? "" : message;
        states.put(id, state);
    }

    private static TaskStatus copy(TaskStatus state) {
        TaskStatus copy = new TaskStatus();
        copy.id = state.id;
        copy.status = state.status;
        copy.progress = state.progress;
        copy.message = state.message;
        return copy;
    }
}
