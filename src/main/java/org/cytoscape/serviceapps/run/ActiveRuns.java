package org.cytoscape.serviceapps.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The runs in progress, by service app, so uninstalling an app or stopping the Desktop service app
 * can cancel them. Cancelling a run sends {@code DELETE} to its service.
 */
public class ActiveRuns {

    private final Map<String, Set<ServiceAppRun>> runs = new ConcurrentHashMap<>();

    void started(ServiceAppRun run) {
        runs.computeIfAbsent(run.appName(), name -> ConcurrentHashMap.newKeySet()).add(run);
    }

    void finished(ServiceAppRun run) {
        runs.computeIfPresent(
                run.appName(),
                (name, forApp) -> {
                    forApp.remove(run);
                    return forApp.isEmpty() ? null : forApp;
                });
    }

    /** Cancels every run of one app. */
    public void cancelApp(String appName) {
        Set<ServiceAppRun> forApp = runs.get(appName);
        if (forApp != null) {
            new ArrayList<>(forApp).forEach(ServiceAppRun::cancel);
        }
    }

    /** Cancels every run. */
    public void cancelAll() {
        List<ServiceAppRun> all = new ArrayList<>();
        runs.values().forEach(all::addAll);
        all.forEach(ServiceAppRun::cancel);
    }

    /** How many runs of an app are in progress. */
    public int count(String appName) {
        Set<ServiceAppRun> forApp = runs.get(appName);
        return forApp == null ? 0 : forApp.size();
    }
}
