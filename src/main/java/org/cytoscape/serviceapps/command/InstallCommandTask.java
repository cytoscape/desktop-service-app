package org.cytoscape.serviceapps.command;

import java.util.Objects;

import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.client.ServiceAppClient;
import org.cytoscape.serviceapps.client.ServiceAppClientFactory;
import org.cytoscape.serviceapps.client.ServiceAppException;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;

/**
 * {@code serviceapps install name= version= url=}: installs a service app, or replaces the
 * installed one with the same name.
 *
 * <p>Fetches the app's metadata first and refuses if the service reports a different name or
 * version than the caller asked for, which catches a mistyped or stale URL. Like a jar app install
 * from the App Store, there is no confirmation prompt.
 */
public class InstallCommandTask extends JsonResultTask {

    public static final String NAME = "install";
    public static final String DESCRIPTION = "Install or upgrade a service app";
    public static final String LONG_DESCRIPTION =
            "Fetches the service app's metadata from url, checks that it reports the given name"
                    + " and version, and installs it. An installed app with the same name is"
                    + " replaced, which is how upgrades work. The text reply contains 'installed'"
                    + " on success.";
    public static final String EXAMPLE_JSON =
            "{\"status\":\"installed\",\"name\":\"Hello World\",\"version\":\"1.0\"}";

    @Tunable(
            description = "Service app name",
            longDescription = "The name the service app reports in its metadata.",
            exampleStringValue = "Hello World",
            required = true,
            context = "nogui")
    public String name;

    @Tunable(
            description = "Service app version",
            longDescription = "The version the service app reports in its metadata.",
            exampleStringValue = "1.0",
            required = true,
            context = "nogui")
    public String version;

    @Tunable(
            description = "Service app URL",
            longDescription = "The service app's endpoint: an http or https URL.",
            exampleStringValue = "https://example.org/hello",
            required = true,
            context = "nogui")
    public String url;

    private final ServiceAppRegistry registry;
    private final ServiceAppClientFactory clients;

    public InstallCommandTask(ServiceAppRegistry registry, ServiceAppClientFactory clients) {
        this.registry = registry;
        this.clients = clients;
    }

    @Override
    public void run(TaskMonitor monitor) {
        String wantedName = required(name, "name");
        String wantedVersion = required(version, "version");
        ServiceAppClient client = clients.forUrl(required(url, "url"));
        monitor.setTitle("Install service app " + wantedName);

        ServiceMetadata meta;
        try {
            meta = client.getMetadata();
        } catch (ServiceAppException e) {
            throw new IllegalStateException(
                    "could not install " + wantedName + ": " + e.getMessage(), e);
        }
        if (!Objects.equals(wantedName, meta.name)
                || !Objects.equals(wantedVersion, meta.version)) {
            throw new IllegalStateException(
                    "the service at "
                            + client.baseUrl()
                            + " reports name '"
                            + meta.name
                            + "' version '"
                            + meta.version
                            + "', not name '"
                            + wantedName
                            + "' version '"
                            + wantedVersion
                            + "'");
        }

        registry.install(wantedName, wantedVersion, client.baseUrl(), meta);

        JsonObject json = new JsonObject();
        json.addProperty("status", "installed");
        json.addProperty("name", wantedName);
        json.addProperty("version", wantedVersion);
        setResult(json, "installed " + wantedName + " " + wantedVersion);
    }
}
