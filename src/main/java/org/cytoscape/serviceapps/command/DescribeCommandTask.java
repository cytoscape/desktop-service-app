package org.cytoscape.serviceapps.command;

import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.app.AppSupport;
import org.cytoscape.serviceapps.call.ResultActionEffects;
import org.cytoscape.serviceapps.call.ServiceInputSchema;
import org.cytoscape.serviceapps.call.UiTargetResolver;
import org.cytoscape.serviceapps.cx.Cx2Support;
import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;

/**
 * {@code serviceapps describe name=}: an installed service app's metadata and the JSON Schemas of
 * {@code task call}'s two JSON arguments for it.
 *
 * <p>Command metadata is fixed per command, so it can't describe one app's parameters; this does.
 */
public class DescribeCommandTask extends JsonResultTask {

    public static final String NAME = "describe";
    public static final String DESCRIPTION =
            "Describe an installed service app found with serviceapps list: what it does, the JSON"
                    + " Schema of its inputs for task call, and the results it applies to Desktop";
    public static final String LONG_DESCRIPTION =
            "Describes one installed service app, by a name from serviceapps list. Read it before"
                    + " serviceapps task call.\n\n"
                    + "Returns {metadata, serviceInputSchema, uiInputSchema, resultActions,"
                    + " unsupportedReason?}.\n\n"
                    + "- metadata: the app's service metadata, as the service reports it.\n"
                    + "- serviceInputSchema: the JSON Schema of task call's serviceInput argument,"
                    + " {parameters, inputColumns}, generated from the same rules as the app's"
                    + " dialog: types, enums, patterns, bounds, defaults and required fields. Column"
                    + " pickers name their table and type filter; the columns come from the"
                    + " network task call targets.\n"
                    + "- uiInputSchema: the JSON Schema of task call's optional uiInput argument,"
                    + " {networkId, networkViewId}.\n"
                    + "- resultActions: the app's outputs, one entry per result action it declares:"
                    + " {action, effect, targets}. effect says what the action changes in Desktop;"
                    + " targets names the uiInput fields that choose where it applies. A service can"
                    + " also return actions it does not declare; task status reports what was"
                    + " applied.\n"
                    + "- unsupportedReason: present when Cytoscape Desktop can't run the app.";
    public static final String EXAMPLE_JSON =
            "{\"metadata\":{\"name\":\"Hello World\",\"version\":\"1.0\"},"
                    + "\"serviceInputSchema\":{\"type\":\"object\",\"properties\":{\"parameters\":"
                    + "{\"type\":\"object\",\"properties\":{\"Greeting\":{\"type\":\"string\","
                    + "\"default\":\"hello\"}},\"required\":[],\"additionalProperties\":false},"
                    + "\"inputColumns\":{\"type\":\"object\",\"properties\":{},\"required\":[],"
                    + "\"additionalProperties\":false}},\"required\":[],"
                    + "\"additionalProperties\":false},"
                    + "\"uiInputSchema\":{\"type\":\"object\",\"properties\":{\"networkId\":"
                    + "{\"type\":\"integer\"},\"networkViewId\":{\"type\":\"integer\"}},"
                    + "\"required\":[],\"additionalProperties\":false},"
                    + "\"resultActions\":[{\"action\":\"updateTables\",\"effect\":\"Adds or fills in"
                    + " node or edge columns of the target network; rows for IDs not in the network"
                    + " are skipped.\",\"targets\":[\"networkId\"]}]}";

    @Tunable(
            description = "Service app name",
            longDescription = "The name of an installed service app, as serviceapps list shows it.",
            exampleStringValue = "Hello World",
            required = true,
            context = "nogui")
    public String name;

    private final ServiceAppRegistry registry;
    private final Cx2Support cx2;

    public DescribeCommandTask(ServiceAppRegistry registry, Cx2Support cx2) {
        this.registry = registry;
        this.cx2 = cx2;
    }

    @Override
    public void run(TaskMonitor monitor) {
        String target = required(name, "name");
        ServiceMetadata meta = registry.requireMeta(target);
        monitor.setTitle("Describe service app " + target);

        JsonObject json = new JsonObject();
        json.add("metadata", Json.tree(meta));
        json.add("serviceInputSchema", ServiceInputSchema.of(meta));
        json.add("uiInputSchema", UiTargetResolver.schema());
        json.add("resultActions", ResultActionEffects.of(meta));
        AppSupport.unsupportedReason(meta, cx2.available())
                .ifPresent(reason -> json.addProperty("unsupportedReason", reason));
        setResult(json, Json.writeWithNulls(json));
    }
}
