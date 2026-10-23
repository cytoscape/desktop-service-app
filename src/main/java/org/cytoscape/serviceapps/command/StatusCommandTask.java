package org.cytoscape.serviceapps.command;

import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.client.ServiceAppClientFactory;
import org.cytoscape.serviceapps.client.ServiceAppStatusChecker;
import org.cytoscape.serviceapps.model.Json;
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
    public static final String DESCRIPTION =
            "Check that an installed service app's service is reachable";
    public static final String LONG_DESCRIPTION =
            "Calls the installed service app's GET /status endpoint and returns {name, reachable,"
                    + " httpStatus, serviceStatus, message?}. httpStatus is null when nothing"
                    + " answered. serviceStatus is the status the service reported, such as 'ok'."
                    + " message, present when the check found a problem, says what.";
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
    private final ServiceAppStatusChecker checker;

    public StatusCommandTask(ServiceAppRegistry registry, ServiceAppClientFactory clients) {
        this.registry = registry;
        this.checker = new ServiceAppStatusChecker(clients);
    }

    @Override
    public void run(TaskMonitor monitor) {
        String target = required(name, "name");
        InstalledServiceApp app = registry.require(target);
        monitor.setTitle("Check service app " + target);

        ServiceAppStatusChecker.Result result = checker.check(app.url());
        JsonObject json = new JsonObject();
        json.addProperty("name", target);
        json.addProperty("reachable", result.reachable());
        // addProperty stores a null value as JSON null.
        json.addProperty("httpStatus", result.httpStatus());
        json.addProperty("serviceStatus", result.serviceStatus());
        if (result.message() != null) {
            json.addProperty("message", result.message());
        }
        registry.setReachable(target, result.reachable());
        setResult(json, Json.writeWithNulls(json));
    }
}
