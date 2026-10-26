package org.cytoscape.serviceapps.manager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cytoscape.serviceapps.client.ServiceAppStatusChecker;
import org.cytoscape.serviceapps.launch.ServiceAppLauncher;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.InstalledServiceApp;
import org.cytoscape.serviceapps.registry.RegistryEvent;
import org.cytoscape.serviceapps.registry.RegistryListener;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;

/**
 * What the Service Apps dialog shows and does, without Swing.
 *
 * <p>Rows come from {@link ServiceAppRegistry#list()}, sorted by name. Opening the model starts a
 * live status check for each app off the calling thread, with the same check as {@code serviceapps
 * status}; each row shows {@code Checking…} until its check finishes. While open, the model follows
 * registry events, so installs and uninstalls made elsewhere show up. Closing stops pending checks.
 */
public class ServiceAppManagerModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceAppManagerModel.class);

    /** An app's live status in the dialog. */
    public enum Status {
        CHECKING("Checking…"),
        ONLINE("Online"),
        OFFLINE("Offline");

        private final String text;

        Status(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }

    /**
     * One row.
     *
     * @param description the app's description from its metadata; blank when it has none
     * @param author the app's author from its metadata; blank when it has none
     * @param invokeDisabledReason why Desktop can't run the app, or null when it can
     * @param statusDetail what the status check found, for the tooltip; null while checking
     */
    public record Row(
            String name,
            String version,
            String url,
            String description,
            String author,
            Status status,
            String statusDetail,
            String invokeDisabledReason) {

        public boolean invokable() {
            return invokeDisabledReason == null;
        }
    }

    private record Checked(Status status, String detail) {}

    private final ServiceAppRegistry registry;
    private final ServiceAppLauncher launcher;
    private final ServiceAppStatusChecker checker;
    private final ExecutorService executor;

    private final Map<String, Checked> statuses = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> checks = new ConcurrentHashMap<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private final RegistryListener registryListener = this::registryChanged;
    private volatile boolean open;

    public ServiceAppManagerModel(
            ServiceAppRegistry registry,
            ServiceAppLauncher launcher,
            ServiceAppStatusChecker checker,
            ExecutorService executor) {
        this.registry = registry;
        this.launcher = launcher;
        this.checker = checker;
        this.executor = executor;
    }

    /** Starts following the registry and checks every app's status again. */
    public void open() {
        open = true;
        statuses.clear();
        registry.addListener(registryListener);
        for (InstalledServiceApp app : registry.list()) {
            check(app);
        }
        changed();
    }

    /** Stops following the registry and cancels pending status checks. */
    public void close() {
        open = false;
        registry.removeListener(registryListener);
        checks.values().forEach(check -> check.cancel(true));
        checks.clear();
    }

    public boolean isOpen() {
        return open;
    }

    /** Pending status checks, for tests. */
    int pendingChecks() {
        return (int) checks.values().stream().filter(check -> !check.isDone()).count();
    }

    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (InstalledServiceApp app : registry.list()) {
            Checked checked = statuses.getOrDefault(app.name(), new Checked(Status.CHECKING, null));
            Optional<ServiceMetadata> meta = registry.getMeta(app.name());
            rows.add(
                    new Row(
                            app.name(),
                            app.version(),
                            app.url(),
                            meta.map(m -> ServiceMetadata.oneLine(m.description)).orElse(""),
                            meta.map(m -> ServiceMetadata.oneLine(m.author)).orElse(""),
                            checked.status(),
                            checked.detail(),
                            launcher.unavailableReason(app.name()).orElse(null)));
        }
        rows.sort(Comparator.comparing(Row::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    /** Called with no arguments, on any thread, whenever the rows may have changed. */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    /** Runs the app: the same launch as its menu entry. */
    public void invoke(String name) {
        launcher.launch(name);
    }

    /** Uninstall: the same path as {@code serviceapps uninstall}. */
    public void uninstall(String name) {
        registry.uninstall(name);
    }

    private void registryChanged(RegistryEvent event) {
        if (!open) {
            return;
        }
        if (event.type() == RegistryEvent.Type.ADDED) {
            statuses.remove(event.name());
            registry.get(event.name()).ifPresent(this::check);
        } else if (event.type() == RegistryEvent.Type.REMOVED) {
            statuses.remove(event.name());
            Future<?> check = checks.remove(event.name());
            if (check != null) {
                check.cancel(true);
            }
        }
        changed();
    }

    private void check(InstalledServiceApp app) {
        try {
            checks.put(
                    app.name(),
                    executor.submit(
                            () -> {
                                Checked checked = checkNow(app);
                                if (!open || Thread.currentThread().isInterrupted()) {
                                    return;
                                }
                                statuses.put(app.name(), checked);
                                // Keeps the menu entry in step with what was found.
                                registry.setReachable(
                                        app.name(), checked.status() == Status.ONLINE);
                                changed();
                            }));
        } catch (RejectedExecutionException e) {
            // shutting down
        }
    }

    /** Never throws: a check that fails unexpectedly shows the app Offline, saying why. */
    private Checked checkNow(InstalledServiceApp app) {
        try {
            ServiceAppStatusChecker.Result result = checker.check(app.url());
            return result.reachable()
                    ? new Checked(Status.ONLINE, "Service status: " + result.serviceStatus())
                    : new Checked(Status.OFFLINE, result.message());
        } catch (RuntimeException e) {
            LOGGER.warn("Status check for '{}' failed", app.name(), e);
            return new Checked(Status.OFFLINE, "The status check failed: " + e.getMessage());
        }
    }

    private void changed() {
        changeListeners.forEach(Runnable::run);
    }
}
