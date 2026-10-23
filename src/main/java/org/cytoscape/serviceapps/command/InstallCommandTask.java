package org.cytoscape.serviceapps.command;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.client.ServiceAppClient;
import org.cytoscape.serviceapps.client.ServiceAppClientFactory;
import org.cytoscape.serviceapps.client.ServiceAppException;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;

/**
 * {@code serviceapps install url= [name=] [version=]}: installs a service app, or replaces the
 * installed one with the same name.
 *
 * <p>Fetches the app's metadata first and installs it under the name and version the service
 * reports. {@code name} and {@code version} are optional checks: when given, the install is refused
 * if the service reports something different, which catches a mistyped or stale URL. The App Store
 * page passes both, so the release it shows is the one installed. Like a jar app install from the
 * App Store, there is no confirmation prompt.
 */
public class InstallCommandTask extends JsonResultTask {

    public static final String NAME = "install";
    public static final String DESCRIPTION =
            "Install or upgrade a service app from its service URL";
    public static final String LONG_DESCRIPTION =
            "Fetches the service app's metadata from url and installs it under the name and"
                    + " version the service reports. name and version are optional: when given,"
                    + " the install is refused unless the service reports exactly that name or"
                    + " version. An installed app with the same name is replaced, which is how"
                    + " upgrades work. Returns the name and version installed; the text reply"
                    + " contains 'installed' on success.";
    public static final String EXAMPLE_JSON =
            "{\"status\":\"installed\",\"name\":\"Hello World\",\"version\":\"1.0\"}";

    @Tunable(
            description = "Service app name (optional check)",
            longDescription =
                    "Optional. The name the service app must report in its metadata; the install"
                            + " is refused if it reports another. Omitted: the reported name is"
                            + " used.",
            exampleStringValue = "Hello World",
            context = "nogui")
    public String name;

    @Tunable(
            description = "Service app version (optional check)",
            longDescription =
                    "Optional. The version the service app must report in its metadata; the"
                            + " install is refused if it reports another. Omitted: the reported"
                            + " version is used.",
            exampleStringValue = "1.0",
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
        ServiceAppClient client = clients.forUrl(required(url, "url"));
        String wantedName = optional(name);
        String wantedVersion = optional(version);
        monitor.setTitle(
                "Install service app " + (wantedName != null ? wantedName : client.baseUrl()));

        ServiceMetadata meta;
        try {
            meta = client.getMetadata();
        } catch (ServiceAppException e) {
            throw new IllegalStateException(
                    "could not install "
                            + (wantedName != null
                                    ? wantedName
                                    : "the service app at " + client.baseUrl())
                            + ": "
                            + e.getMessage(),
                    e);
        }

        List<String> problems = new ArrayList<>();
        check("name", wantedName, meta.name, problems);
        check("version", wantedVersion, meta.version, problems);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "the service at "
                            + client.baseUrl()
                            + " "
                            + String.join(" and ", problems)
                            + "; nothing was installed");
        }

        registry.install(meta.name, meta.version, client.baseUrl(), meta);

        JsonObject json = new JsonObject();
        json.addProperty("status", "installed");
        json.addProperty("name", meta.name);
        json.addProperty("version", meta.version);
        setResult(json, "installed " + meta.name + " " + meta.version);
    }

    /** A blank argument counts as omitted. */
    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** The service must report a value; when the caller gave one, it must match exactly. */
    private static void check(String field, String wanted, String reported, List<String> problems) {
        if (reported == null || reported.isBlank()) {
            problems.add("reports no " + field);
        } else if (wanted != null && !wanted.equals(reported)) {
            problems.add("reports " + field + " '" + reported + "', not '" + wanted + "'");
        }
    }
}
