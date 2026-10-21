package org.cytoscape.serviceapps.command;

import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;

/** {@code serviceapps uninstall name=}: removes an installed service app. */
public class UninstallCommandTask extends JsonResultTask {

    public static final String NAME = "uninstall";
    public static final String DESCRIPTION = "Uninstall a service app";
    public static final String LONG_DESCRIPTION =
            "Removes the installed service app with this name. Fails if no such app is installed."
                    + " The text reply contains 'uninstalled' on success.";
    public static final String EXAMPLE_JSON =
            "{\"status\":\"uninstalled\",\"name\":\"Hello World\"}";

    @Tunable(
            description = "Service app name",
            longDescription = "The name of an installed service app, as serviceapps list shows it.",
            exampleStringValue = "Hello World",
            required = true,
            context = "nogui")
    public String name;

    private final ServiceAppRegistry registry;

    public UninstallCommandTask(ServiceAppRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(TaskMonitor monitor) {
        String target = required(name, "name");
        monitor.setTitle("Uninstall service app " + target);
        if (!registry.uninstall(target)) {
            throw new IllegalArgumentException(
                    "no service app named '" + target + "' is installed");
        }
        JsonObject json = new JsonObject();
        json.addProperty("status", "uninstalled");
        json.addProperty("name", target);
        setResult(json, "uninstalled " + target);
    }
}
