package org.cytoscape.serviceapps.run;

import java.time.Duration;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.serviceapps.client.ServiceAppClient;
import org.cytoscape.serviceapps.client.ServiceAppException;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.model.TaskStatus;
import org.cytoscape.serviceapps.render.RenderException;
import org.cytoscape.serviceapps.render.RenderReport;
import org.cytoscape.serviceapps.render.RenderServiceAppUI;
import org.cytoscape.serviceapps.render.UIInfo;
import org.cytoscape.serviceapps.request.BuiltRequest;
import org.cytoscape.serviceapps.request.RequestBuilder;
import org.cytoscape.serviceapps.request.RequestException;
import org.cytoscape.serviceapps.request.ServiceInput;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

/**
 * One run of a service app, as a Desktop task. The target network, view and input are fixed when
 * the run is created.
 *
 * <ol>
 *   <li>Build the request and submit it.
 *   <li>Poll the task's status, showing its progress and message.
 *   <li>Stop on {@code complete}, {@code failed} or progress 100.
 *   <li>Fetch the result, apply it with {@link RenderServiceAppUI}, then delete the task.
 * </ol>
 *
 * Cancel deletes the remote task and applies nothing. A failed clean-up is logged and the results
 * stay. A failure of the service starts with {@link #SERVICE_FAILED}; a failure while applying the
 * result starts with {@link RenderException#FAILED}. The same message reaches the menu path
 * (Desktop's task error dialog) and {@code task status}. The run fails after {@link
 * RunSettings#MAX_FAILED_POLLS} failed status checks in a row, or when it takes longer than the
 * configured timeout.
 *
 * @see <a
 *     href="https://github.com/cytoscape/cytoscape-web/wiki/Specification-for-Service-App-in-Cytoscape-Web-(draft-v2)">Specification</a>
 */
public class ServiceAppRun extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceAppRun.class);

    /** How every failure of the service itself starts, on both entry paths. */
    public static final String SERVICE_FAILED = "Service task failed: ";

    /**
     * The share of the progress bar for the service's own work; applying the result fills the rest.
     */
    private static final double SERVICE_SHARE = 0.9;

    /** Everything a run needs. */
    public record Plan(
            String appName,
            ServiceMetadata meta,
            ServiceInput input,
            CyNetwork target,
            CyNetworkView view) {}

    private final Plan plan;
    private final ServiceAppClient client;
    private final RequestBuilder requests;
    private final RenderServiceAppUI renderer;
    private final Supplier<RunSettings> settings;
    private final ActiveRuns active;
    private final RunListener listener;

    private volatile String remoteId;
    private boolean deleted; // guarded by deleteQuietly's lock
    private volatile double progress;

    public ServiceAppRun(
            Plan plan,
            ServiceAppClient client,
            RequestBuilder requests,
            RenderServiceAppUI renderer,
            Supplier<RunSettings> settings,
            ActiveRuns active,
            RunListener listener) {
        this.plan = plan;
        this.client = client;
        this.requests = requests;
        this.renderer = renderer;
        this.settings = settings;
        this.active = active;
        this.listener = listener == null ? RunListener.NONE : listener;
    }

    public String appName() {
        return plan.appName();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void run(TaskMonitor monitor) {
        active.started(this);
        try {
            execute(monitor);
        } catch (RuntimeException e) {
            listener.stateChanged(TaskStatus.FAILED, progress, e.getMessage());
            throw e;
        } finally {
            active.finished(this);
        }
    }

    private void execute(TaskMonitor monitor) {
        RunSettings limits = settings.get();
        long deadline = System.nanoTime() + limits.timeout().toNanos();
        monitor.setTitle("Run " + plan.appName());
        listener.stateChanged(TaskStatus.SUBMITTED, 0, "Submitting");

        BuiltRequest built;
        try {
            built = requests.build(plan.meta(), plan.input(), plan.target(), plan.view());
        } catch (RequestException e) {
            throw new RunException(e.getMessage(), e);
        }
        monitor.setStatusMessage("Submitting to " + plan.appName());
        try {
            remoteId = client.submit(built.request());
        } catch (ServiceAppException e) {
            throw serviceFailed(plan.appName() + " didn't accept the task: " + e.getMessage(), e);
        }

        TaskStatus status = poll(monitor, limits, deadline);
        if (status == null) {
            return; // cancelled
        }

        TaskStatus result;
        try {
            result = client.getTaskResult(remoteId);
        } catch (ServiceAppException e) {
            deleteQuietly();
            throw serviceFailed("could not fetch the result: " + e.getMessage(), e);
        }
        if (result.isFailed() || status.isFailed()) {
            deleteQuietly();
            throw serviceFailed(
                    firstNonBlank(
                            result.message, status.message, plan.appName() + " reported a failure"),
                    null);
        }
        if (cancelled) {
            deleteQuietly();
            return;
        }

        monitor.setStatusMessage("Applying the result");
        RenderReport report;
        try {
            report =
                    renderer.render(
                            result.actions(),
                            plan.meta().cyWebActions(),
                            new UIInfo(plan.target(), plan.view(), built.sent()),
                            new ScaledMonitor(monitor, SERVICE_SHARE, 1 - SERVICE_SHARE));
        } catch (RenderException e) {
            deleteQuietly();
            throw new RunException(e.getMessage(), e);
        } catch (RuntimeException e) {
            deleteQuietly();
            throw new RunException(RenderException.FAILED + ": " + e.getMessage(), e);
        }
        deleteQuietly();
        built.notes().forEach(report::note);

        progress = 100;
        monitor.setProgress(1.0);
        monitor.setStatusMessage(report.summary());
        if (!report.notes().isEmpty()) {
            monitor.showMessage(TaskMonitor.Level.INFO, report.summary());
        }
        listener.stateChanged(TaskStatus.COMPLETE, 100, report.summary());
    }

    /** Polls until the task finishes; null when cancelled. */
    private TaskStatus poll(TaskMonitor monitor, RunSettings limits, long deadline) {
        int failedPolls = 0;
        while (true) {
            if (cancelled) {
                deleteQuietly();
                listener.stateChanged(TaskStatus.FAILED, progress, "Cancelled");
                return null;
            }
            TaskStatus status;
            try {
                status = client.getTaskStatus(remoteId);
                failedPolls = 0;
            } catch (ServiceAppException e) {
                failedPolls++;
                LOGGER.warn(
                        "Status check {} of {} for {} failed: {}",
                        failedPolls,
                        RunSettings.MAX_FAILED_POLLS,
                        plan.appName(),
                        e.getMessage());
                if (failedPolls >= RunSettings.MAX_FAILED_POLLS) {
                    deleteQuietly();
                    throw serviceFailed(
                            "lost contact with " + plan.appName() + ": " + e.getMessage(), e);
                }
                status = null;
            }
            if (status != null) {
                progress =
                        status.progress == null
                                ? progress
                                : Math.max(0, Math.min(100, status.progress));
                monitor.setProgress(progress / 100 * SERVICE_SHARE);
                if (status.message != null && !status.message.isBlank()) {
                    monitor.setStatusMessage(status.message);
                }
                listener.stateChanged(TaskStatus.PROCESSING, progress, status.message);
                if (status.isComplete() || status.isFailed() || progress >= 100) {
                    return status;
                }
            }
            if (System.nanoTime() > deadline) {
                deleteQuietly();
                throw serviceFailed(
                        plan.appName()
                                + " took longer than "
                                + describe(limits.timeout())
                                + " and was stopped.",
                        null);
            }
            sleep(limits.pollInterval());
        }
    }

    @Override
    public void cancel() {
        super.cancel();
        // The poll loop notices within one interval; delete now so the service stops at once.
        deleteQuietly();
    }

    private synchronized void deleteQuietly() {
        if (remoteId == null || deleted) {
            return;
        }
        deleted = true;
        try {
            client.deleteTask(remoteId);
        } catch (RuntimeException e) {
            LOGGER.warn(
                    "Could not delete task {} at {}: {}", remoteId, plan.appName(), e.getMessage());
        }
    }

    private void sleep(Duration interval) {
        try {
            Thread.sleep(interval.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelled = true;
        }
    }

    private static RunException serviceFailed(String reason, Throwable cause) {
        return new RunException(SERVICE_FAILED + reason, cause);
    }

    private static String firstNonBlank(String... texts) {
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static String describe(Duration duration) {
        if (duration.toMinutes() >= 1) {
            return duration.toMinutes() + " minute(s)";
        }
        return duration.toSeconds() >= 1
                ? duration.toSeconds() + " second(s)"
                : duration.toMillis() + " ms";
    }

    /** Maps the renderer's 0..1 progress into a slice of the run's progress. */
    private record ScaledMonitor(TaskMonitor monitor, double start, double span)
            implements TaskMonitor {
        @Override
        public void setTitle(String title) {
            monitor.setTitle(title);
        }

        @Override
        public void setProgress(double value) {
            monitor.setProgress(start + value * span);
        }

        @Override
        public void setStatusMessage(String message) {
            monitor.setStatusMessage(message);
        }

        @Override
        public void showMessage(Level level, String message) {
            monitor.showMessage(level, message);
        }
    }
}
