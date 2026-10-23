package org.cytoscape.serviceapps.launch;

import java.util.Optional;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.serviceapps.app.RunPrecondition;
import org.cytoscape.serviceapps.cx.Cx2Support;
import org.cytoscape.serviceapps.form.FormSpec;
import org.cytoscape.serviceapps.form.FormSpecBuilder;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.InstalledServiceApp;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.serviceapps.run.ServiceAppRunFactory;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;
import org.cytoscape.work.TaskMonitor;

/**
 * The one way a service app is started from Desktop's UI, shared by its menu entry and the
 * manager's Invoke. Checks {@link RunPrecondition} first and tells the user why when the app can't
 * run; otherwise shows the input dialog and runs the app on the current network.
 */
public class ServiceAppLauncher {

    private final ServiceAppRegistry registry;
    private final FormSpecBuilder forms;
    private final TaskManager<?, ?> taskManager;
    private final CyApplicationManager applications;
    private final ServiceAppRunFactory runs;
    private final Cx2Support cx2;
    private final UserMessages messages;

    public ServiceAppLauncher(
            ServiceAppRegistry registry,
            FormSpecBuilder forms,
            TaskManager<?, ?> taskManager,
            CyApplicationManager applications,
            ServiceAppRunFactory runs,
            Cx2Support cx2,
            UserMessages messages) {
        this.registry = registry;
        this.forms = forms;
        this.taskManager = taskManager;
        this.applications = applications;
        this.runs = runs;
        this.cx2 = cx2;
        this.messages = messages;
    }

    /** Why the app can't run right now, for the user, or empty if it can. */
    public Optional<String> whyNot(String name) {
        Optional<InstalledServiceApp> app = registry.get(name);
        Optional<ServiceMetadata> meta = registry.getMeta(name);
        if (app.isEmpty() || meta.isEmpty()) {
            return Optional.of(name + " is no longer installed.");
        }
        return RunPrecondition.check(
                app.get(), meta.get(), applications.getCurrentNetwork(), cx2.available());
    }

    /**
     * Why the app can't run at all right now (unsupported or unreachable), for the manager, or
     * empty if it can.
     */
    public Optional<String> unavailableReason(String name) {
        Optional<InstalledServiceApp> app = registry.get(name);
        Optional<ServiceMetadata> meta = registry.getMeta(name);
        if (app.isEmpty() || meta.isEmpty()) {
            return Optional.of("not installed");
        }
        return RunPrecondition.unavailable(app.get(), meta.get(), cx2.available());
    }

    /**
     * The tasks for one launch: the input dialog and then the run, or a task that only explains why
     * the app can't run.
     */
    public TaskIterator createTaskIterator(String name) {
        Optional<String> blocked = whyNot(name);
        return blocked.isPresent()
                ? new TaskIterator(new ExplainTask(messages, name, blocked.get()))
                : runTasks(name);
    }

    /** Starts the app, or tells the user why it can't run. */
    public void launch(String name) {
        Optional<String> blocked = whyNot(name);
        if (blocked.isPresent()) {
            messages.warn(name, blocked.get());
            return;
        }
        taskManager.execute(runTasks(name));
    }

    private TaskIterator runTasks(String name) {
        ServiceMetadata meta = registry.getMeta(name).orElseThrow();
        CyNetwork target = applications.getCurrentNetwork();
        CyNetworkView view = applications.getCurrentNetworkView();
        if (view != null && view.getModel() != target) {
            view = null;
        }
        FormSpec spec = forms.build(meta, registry.getLastValues(name), target);
        return new TaskIterator(new ServiceAppRunTask(name, spec, target, view, registry, runs));
    }

    /** Shows why an app can't run, for a menu entry clicked when Desktop thought it could. */
    static final class ExplainTask extends AbstractTask {
        private final UserMessages messages;
        private final String title;
        private final String message;

        ExplainTask(UserMessages messages, String title, String message) {
            this.messages = messages;
            this.title = title;
            this.message = message;
        }

        @Override
        public void run(TaskMonitor monitor) {
            messages.warn(title, message);
        }
    }
}
