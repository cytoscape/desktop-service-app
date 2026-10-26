package org.cytoscape.serviceapps.command;

import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.app.AppSupport;
import org.cytoscape.serviceapps.cx.Cx2Support;
import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.InstalledServiceApp;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.work.TaskMonitor;

/**
 * {@code serviceapps list}: the installed service apps, and the way to discover them. They aren't
 * registered as Desktop commands, so this command's metadata says what they are and how to describe
 * and run them.
 *
 * <p>The text reply is the same JSON, because the App Store page reads it over CyREST to decide
 * whether to show Install, Installed or Upgrade.
 */
public class ListCommandTask extends JsonResultTask {

    public static final String NAME = "list";
    public static final String DESCRIPTION =
            "Discover the service apps installed in Desktop, more commands for network analysis"
                    + " (such as clustering, enrichment or layout) that are not registered as"
                    + " Desktop commands; learn and run each one with serviceapps describe and task"
                    + " call";
    public static final String LONG_DESCRIPTION =
            "The starting point for finding and running service apps, the remote analysis tools"
                    + " (such as clustering, enrichment or layout) installed in this Cytoscape"
                    + " Desktop. Each one works like an additional command, but it is not registered"
                    + " as a Desktop command, so it does not appear in Desktop's list of available"
                    + " commands. This command is how to discover them. The set changes as service"
                    + " apps are installed and uninstalled, so list them again rather than relying"
                    + " on an earlier answer.\n\n"
                    + "Returns every installed service app as a JSON array of {name, version, url,"
                    + " reachable, description, unsupportedReason}, sorted by name. description is"
                    + " what the app says it does; reachable is the result of the most recent check"
                    + " of its URL; unsupportedReason is null when Desktop can run the app,"
                    + " otherwise why it can't.\n\n"
                    + "For each app:\n"
                    + "1. serviceapps describe name=<name> gives its full description: its"
                    + " metadata, the JSON Schema of its inputs, and the results it applies to"
                    + " Desktop (its outputs).\n"
                    + "2. serviceapps task call app=<name> runs it and returns a task_id.\n"
                    + "3. serviceapps task status task_id=<task_id> reports progress until the run"
                    + " is complete or failed.";
    public static final String EXAMPLE_JSON =
            "[{\"name\":\"Community Detection\",\"version\":\"2.1\","
                    + "\"url\":\"https://example.org/community\",\"reachable\":true,"
                    + "\"description\":\"Finds communities of densely connected nodes and adds a"
                    + " cluster column\",\"unsupportedReason\":null},"
                    + "{\"name\":\"Enrichment Analysis\",\"version\":\"1.4\","
                    + "\"url\":\"https://example.org/enrichment\",\"reachable\":true,"
                    + "\"description\":\"Tests a gene list for enriched pathways and opens the"
                    + " results\",\"unsupportedReason\":null},"
                    + "{\"name\":\"Force Layout\",\"version\":\"1.0\","
                    + "\"url\":\"https://example.org/layout\",\"reachable\":false,"
                    + "\"description\":\"Lays out the network with a force-directed algorithm\","
                    + "\"unsupportedReason\":\"it asks for the network as an edge list, which isn't"
                    + " supported yet\"}]";

    private final ServiceAppRegistry registry;
    private final Cx2Support cx2;

    public ListCommandTask(ServiceAppRegistry registry, Cx2Support cx2) {
        this.registry = registry;
        this.cx2 = cx2;
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
            Optional<ServiceMetadata> meta = registry.getMeta(app.name());
            entry.addProperty(
                    "description",
                    meta.map(m -> ServiceMetadata.oneLine(m.description)).orElse(""));
            entry.addProperty(
                    "unsupportedReason",
                    AppSupport.unsupportedReason(meta.orElse(null), cx2.available()).orElse(null));
            apps.add(entry);
        }
        setResult(apps, Json.writeWithNulls(apps));
    }
}
