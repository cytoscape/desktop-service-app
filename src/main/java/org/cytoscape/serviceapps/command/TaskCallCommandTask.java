package org.cytoscape.serviceapps.command;

import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.app.AppSupport;
import org.cytoscape.serviceapps.app.RunPrecondition;
import org.cytoscape.serviceapps.call.ServiceInputParser;
import org.cytoscape.serviceapps.call.UiTarget;
import org.cytoscape.serviceapps.call.UiTargetResolver;
import org.cytoscape.serviceapps.cx.Cx2Support;
import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.InstalledServiceApp;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.serviceapps.request.ServiceInput;
import org.cytoscape.serviceapps.run.ServiceAppRunFactory;
import org.cytoscape.serviceapps.run.ServiceAppRunRegistry;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;

/**
 * {@code serviceapps task call app= serviceInput= uiInput=}: runs an installed service app in the
 * background, as its menu entry does, and returns a Desktop task ID at once.
 *
 * <p>Everything that can be checked is checked before anything is sent: the app, {@code uiInput}'s
 * targets, whether the app can run on them, and {@code serviceInput} against the dialog's rules.
 * The cached reachability flag doesn't block a call; an unreachable service ends the task as
 * failed.
 */
public class TaskCallCommandTask extends JsonResultTask {

    public static final String NAME = "task call";
    public static final String DESCRIPTION =
            "Run a service app on a network in the background; returns a task_id for task status";
    public static final String LONG_DESCRIPTION =
            "Runs an installed service app exactly as its menu entry does: sends the task request,"
                    + " polls the service, and applies the result to Desktop. Returns {task_id} at"
                    + " once; poll serviceapps task status task_id=<task_id> until status is"
                    + " complete or failed.\n\n"
                    + "Find the app's name with serviceapps list, then run serviceapps describe"
                    + " name=<app> first: it returns the JSON Schemas of"
                    + " both JSON arguments for that app.\n\n"
                    + "- app: the service app's name, as serviceapps list shows it.\n"
                    + "- serviceInput: a JSON *string* such as"
                    + " '{\"parameters\":{\"Resolution\":0.5},\"inputColumns\":{\"Gene names\":\"name\"}}'."
                    + " parameters are keyed by each parameter's displayName (Group/displayName"
                    + " when several share one, as describe shows); inputColumns by each"
                    + " input column's name, with a column of the target network (a list when"
                    + " several may be chosen). Omitted values take their defaults; unknown names"
                    + " are rejected. Values are checked with the same rules as the app's dialog.\n"
                    + "- uiInput: an optional JSON *string* such as '{\"networkId\":52}' naming the"
                    + " Desktop targets: networkId (a network SUID; default the active network) and"
                    + " networkViewId (a view SUID; default every view of the target network)."
                    + " Input data is read from the target network. An ID that doesn't exist is"
                    + " rejected before anything is sent.\n\n"
                    + "Pass both JSON arguments as strings, not objects. An unsupported app, or an"
                    + " app whose network or selection needs aren't met, is rejected with the"
                    + " reason.";
    public static final String EXAMPLE_JSON =
            "{\"task_id\":\"6f1c2b2e-6a8e-4f0b-9a55-2b0c1f9d7e41\"}";

    @Tunable(
            description = "Service app name",
            longDescription = "The name of an installed service app, as serviceapps list shows it.",
            exampleStringValue = "Hello World",
            required = true,
            context = "nogui")
    public String app;

    @Tunable(
            description = "Service app inputs as a JSON string",
            longDescription =
                    "A JSON string {\"parameters\":{...},\"inputColumns\":{...}} that conforms to"
                            + " the serviceInputSchema from serviceapps describe. Omit it for an"
                            + " app that takes no input.",
            exampleStringValue = "{\"parameters\":{\"Greeting\":\"hi\"}}",
            context = "nogui")
    public String serviceInput;

    @Tunable(
            description = "Desktop targets as a JSON string",
            longDescription =
                    "An optional JSON string {\"networkId\":<SUID>,\"networkViewId\":<SUID>}"
                            + " that conforms to the uiInputSchema from serviceapps describe. Omitted:"
                            + " the active network and every one of its views.",
            exampleStringValue = "{\"networkId\":52}",
            context = "nogui")
    public String uiInput;

    private final ServiceAppRegistry registry;
    private final UiTargetResolver targets;
    private final ServiceInputParser inputs;
    private final ServiceAppRunFactory runFactory;
    private final ServiceAppRunRegistry runs;
    private final Cx2Support cx2;

    public TaskCallCommandTask(
            ServiceAppRegistry registry,
            UiTargetResolver targets,
            ServiceInputParser inputs,
            ServiceAppRunFactory runFactory,
            ServiceAppRunRegistry runs,
            Cx2Support cx2) {
        this.registry = registry;
        this.targets = targets;
        this.inputs = inputs;
        this.runFactory = runFactory;
        this.runs = runs;
        this.cx2 = cx2;
    }

    @Override
    public void run(TaskMonitor monitor) {
        String name = required(app, "app");
        InstalledServiceApp installed = registry.require(name);
        ServiceMetadata meta = registry.requireMeta(name);
        monitor.setTitle("Start service app " + name);

        UiTarget target = targets.resolve(uiInput);
        AppSupport.unsupportedReason(meta, cx2.available())
                .ifPresent(
                        reason -> {
                            throw new IllegalArgumentException(
                                    name + " can't run in Cytoscape Desktop: " + reason + ".");
                        });
        if (AppSupport.needsNetwork(meta) && target.network() == null) {
            throw new IllegalArgumentException(
                    name
                            + " works on a network: pass uiInput {\"networkId\":<SUID>} or make a"
                            + " network active.");
        }
        // Reachability is left to the run: the saved flag may be stale.
        InstalledServiceApp assumeReachable = installed.withReachable(true);
        RunPrecondition.check(assumeReachable, meta, target.network(), cx2.available())
                .ifPresent(
                        reason -> {
                            throw new IllegalArgumentException(reason);
                        });
        ServiceInput input = inputs.parse(serviceInput, meta, target.network());

        String taskId =
                runs.start(
                        listener ->
                                runFactory.create(
                                        name, input, target.network(), target.view(), listener));
        JsonObject json = new JsonObject();
        json.addProperty("task_id", taskId);
        setResult(json, Json.writeWithNulls(json));
    }
}
