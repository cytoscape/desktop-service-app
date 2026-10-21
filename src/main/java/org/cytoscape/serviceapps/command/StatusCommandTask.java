package org.cytoscape.serviceapps.command;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.client.ServiceAppClientFactory;
import org.cytoscape.serviceapps.client.ServiceAppException;
import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.model.ServiceStatus;
import org.cytoscape.serviceapps.registry.InstalledServiceApp;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;

/**
 * {@code serviceapps status name=}: a live check that an installed service app answers.
 *
 * <p>Calls the app's {@code GET /status}. The app is reachable when that returns a 2xx reply. The
 * result is saved, so {@code serviceapps list} and the menus reflect it.
 */
public class StatusCommandTask extends JsonResultTask {

    public static final String NAME = "status";
    public static final String DESCRIPTION = "Check that an installed service app is reachable";
    public static final String LONG_DESCRIPTION =
            "Calls the installed service app's GET /status endpoint and returns {name, reachable,"
                    + " httpStatus, serviceStatus}. httpStatus is null when nothing answered."
                    + " serviceStatus is the status the service reported, such as 'ok'.";
    public static final String EXAMPLE_JSON =
            "{\"name\":\"Hello World\",\"reachable\":true,\"httpStatus\":200,"
                    + "\"serviceStatus\":\"ok\"}";

    @Tunable(
            description = "Service app name",
            longDescription = "The name of an installed service app, as serviceapps list shows it.",
            exampleStringValue = "Hello World",
            required = true,
            context = "nogui")
    public String name;

    private final ServiceAppRegistry registry;
    private final ServiceAppClientFactory clients;

    public StatusCommandTask(ServiceAppRegistry registry, ServiceAppClientFactory clients) {
        this.registry = registry;
        this.clients = clients;
    }

    @Override
    public void run(TaskMonitor monitor) {
        String target = required(name, "name");
        InstalledServiceApp app =
                registry.get(target)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "no service app named '"
                                                        + target
                                                        + "' is installed"));
        monitor.setTitle("Check service app " + target);

        JsonObject json = new JsonObject();
        json.addProperty("name", target);
        boolean reachable;
        try {
            ServiceStatus status = clients.forUrl(app.url()).getStatus();
            reachable = true;
            json.addProperty("reachable", true);
            json.addProperty("httpStatus", status.httpStatus);
            json.addProperty("serviceStatus", status.status);
        } catch (ServiceAppException e) {
            reachable = false;
            json.addProperty("reachable", false);
            if (e.httpStatus() == ServiceAppException.NO_RESPONSE) {
                json.add("httpStatus", JsonNull.INSTANCE);
            } else {
                json.addProperty("httpStatus", e.httpStatus());
            }
            json.add("serviceStatus", JsonNull.INSTANCE);
            json.addProperty("message", e.getMessage());
        }
        registry.setReachable(target, reachable);
        setResult(json, Json.writeWithNulls(json));
    }
}
