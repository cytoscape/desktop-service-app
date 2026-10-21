package org.cytoscape.serviceapps.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.registry.InstalledServiceApp;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.work.TaskMonitor;

/**
 * {@code serviceapps list}: the installed service apps.
 *
 * <p>The text reply is the same JSON, because the App Store page reads it over CyREST to decide
 * whether to show Install, Installed or Upgrade.
 */
public class ListCommandTask extends JsonResultTask {

    public static final String NAME = "list";
    public static final String DESCRIPTION = "List the installed service apps";
    public static final String LONG_DESCRIPTION =
            "Returns every installed service app as a JSON array of {name, version, url,"
                    + " reachable}, sorted by name. reachable is the result of the most recent"
                    + " check of the app's URL.";
    public static final String EXAMPLE_JSON =
            "[{\"name\":\"Hello World\",\"version\":\"1.0\","
                    + "\"url\":\"https://example.org/hello\",\"reachable\":true}]";

    private final ServiceAppRegistry registry;

    public ListCommandTask(ServiceAppRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(TaskMonitor monitor) {
        monitor.setTitle("List service apps");
        JsonArray apps = new JsonArray();
        for (InstalledServiceApp app : registry.list()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", app.name());
            entry.addProperty("version", app.version());
            entry.addProperty("url", app.url());
            entry.addProperty("reachable", app.reachable());
            apps.add(entry);
        }
        setResult(apps, Json.writeWithNulls(apps));
    }
}
