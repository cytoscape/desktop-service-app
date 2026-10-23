package org.cytoscape.serviceapps.launch;

import java.io.IOException;
import java.util.List;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.serviceapps.form.FormSpec;
import org.cytoscape.serviceapps.form.FormValues;
import org.cytoscape.serviceapps.form.ServiceAppForm;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.serviceapps.run.RunListener;
import org.cytoscape.serviceapps.run.ServiceAppRunFactory;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ProvidesTitle;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;
import org.cytoscape.work.TunableValidator;

/**
 * The input dialog for one service app: Desktop's task dialog draws {@link #form} with {@code
 * ServiceAppFormHandler}. On OK the values are remembered and the run is queued after this task.
 */
public class ServiceAppRunTask extends AbstractTask implements TunableValidator {

    @Tunable(description = "", context = Tunable.GUI_CONTEXT)
    public ServiceAppForm form;

    private final String appName;
    private final CyNetwork target;
    private final CyNetworkView view;
    private final ServiceAppRegistry registry;
    private final ServiceAppRunFactory runs;

    public ServiceAppRunTask(
            String appName,
            FormSpec spec,
            CyNetwork target,
            CyNetworkView view,
            ServiceAppRegistry registry,
            ServiceAppRunFactory runs) {
        this.appName = appName;
        this.form = new ServiceAppForm(spec);
        this.target = target;
        this.view = view;
        this.registry = registry;
        this.runs = runs;
    }

    @ProvidesTitle
    public String getTitle() {
        FormSpec spec = form.spec();
        return spec.version() == null || spec.version().isBlank()
                ? spec.appName()
                : spec.appName() + " " + spec.version();
    }

    @Override
    public ValidationState getValidationState(Appendable message) {
        List<String> problems = form.spec().validate(form.values());
        if (problems.isEmpty()) {
            return ValidationState.OK;
        }
        try {
            message.append(String.join("\n", problems));
        } catch (IOException e) {
            // the message is only a hint
        }
        return ValidationState.INVALID;
    }

    @Override
    public void run(TaskMonitor monitor) {
        FormValues values = form.values();
        registry.saveLastValues(appName, values.toJson());
        insertTasksAfterCurrentTask(
                runs.create(appName, values.toServiceInput(), target, view, RunListener.NONE));
    }

    public CyNetwork target() {
        return target;
    }
}
