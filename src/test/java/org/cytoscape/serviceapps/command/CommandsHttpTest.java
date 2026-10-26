package org.cytoscape.serviceapps.command;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.serviceapps.client.ClientSettings;
import org.cytoscape.serviceapps.client.DefaultServiceAppClientFactory;
import org.cytoscape.serviceapps.client.ServiceAppClientFactory;
import org.cytoscape.serviceapps.cx.Cx2Support;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.FixtureServerExtension;
import org.cytoscape.serviceapps.fixture.ScenarioCatalog;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.serviceapps.testsupport.Waits;
import org.cytoscape.work.TaskMonitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Every {@code serviceapps} command against the hello-world fixture, over real HTTP, with a real
 * registry on disk.
 */
@ExtendWith(FixtureServerExtension.class)
class CommandsHttpTest {

    @TempDir Path dir;

    private final TaskMonitor monitor = mock(TaskMonitor.class);
    private final ServiceAppClientFactory clients =
            new DefaultServiceAppClientFactory(() -> ClientSettings.DEFAULTS);
    private ServiceAppRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ServiceAppRegistry.inDirectory(dir);
    }

    private InstallCommandTask install(String name, String version, String url) {
        InstallCommandTask task = new InstallCommandTask(registry, clients);
        task.name = name;
        task.version = version;
        task.url = url;
        return task;
    }

    private JsonArray list() {
        ListCommandTask task = new ListCommandTask(registry, mock(Cx2Support.class));
        task.run(monitor);
        return JsonParser.parseString(task.getResults(String.class)).getAsJsonArray();
    }

    private JsonObject status(String name) {
        StatusCommandTask task = new StatusCommandTask(registry, clients);
        task.name = name;
        task.run(monitor);
        return CommandResults.json(task);
    }

    @Test
    void installListStatusUninstall(FixtureServer fixture) {
        String url = fixture.url(ScenarioCatalog.HELLO);

        InstallCommandTask installed = install("Hello World", "1.0", url);
        installed.run(monitor);
        assertTrue(installed.getResults(String.class).contains("installed"));

        JsonArray apps = list();
        assertEquals(1, apps.size());
        assertEquals("Hello World", apps.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(url, apps.get(0).getAsJsonObject().get("url").getAsString());

        JsonObject status = status("Hello World");
        assertTrue(status.get("reachable").getAsBoolean());
        assertEquals(200, status.get("httpStatus").getAsInt());
        assertEquals("ok", status.get("serviceStatus").getAsString());

        UninstallCommandTask uninstall = new UninstallCommandTask(registry);
        uninstall.name = "Hello World";
        uninstall.run(monitor);
        assertTrue(uninstall.getResults(String.class).contains("uninstalled"));
        assertEquals(0, list().size());
        assertTrue(ServiceAppRegistry.inDirectory(dir).list().isEmpty());
    }

    @Test
    void installRefusesANameOrVersionMismatch(FixtureServer fixture) {
        String url = fixture.url(ScenarioCatalog.HELLO);

        assertThrows(
                IllegalStateException.class, () -> install("Hello World", "9.9", url).run(monitor));
        assertThrows(
                IllegalStateException.class, () -> install("Goodbye", "1.0", url).run(monitor));

        assertEquals(0, list().size());
    }

    @Test
    void installReportsAnUnreachableUrl() throws Exception {
        int port = Waits.freePort();
        String url = "http://127.0.0.1:" + port + "/hello";

        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> install("Hello World", "1.0", url).run(monitor));

        assertTrue(e.getMessage().contains("could not reach"), e.getMessage());
        assertEquals(0, list().size());
    }

    @Test
    void installingTheNextVersionReplacesTheApp(FixtureServer fixture) {
        install("Hello World", "1.0", fixture.url(ScenarioCatalog.HELLO)).run(monitor);

        install("Hello World", "2.0", fixture.url(ScenarioCatalog.HELLO_V2)).run(monitor);

        JsonArray apps = list();
        assertEquals(1, apps.size());
        JsonObject app = apps.get(0).getAsJsonObject();
        assertEquals("2.0", app.get("version").getAsString());
        assertEquals(fixture.url(ScenarioCatalog.HELLO_V2), app.get("url").getAsString());
    }

    @Test
    void statusMarksAMovedServiceUnreachable(FixtureServer fixture) {
        install("Hello World", "1.0", fixture.url(ScenarioCatalog.HELLO)).run(monitor);
        registry.install(
                "Hello World",
                "1.0",
                fixture.baseUrl() + "/moved-away",
                registry.getMeta("Hello World").orElseThrow());

        JsonObject status = status("Hello World");

        assertFalse(status.get("reachable").getAsBoolean());
        assertEquals(404, status.get("httpStatus").getAsInt());
        assertFalse(list().get(0).getAsJsonObject().get("reachable").getAsBoolean());
    }

    @Test
    void uninstallingAnUnknownAppFails() {
        UninstallCommandTask task = new UninstallCommandTask(registry);
        task.name = "Never Installed";

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> task.run(monitor));
        assertTrue(e.getMessage().contains("Never Installed"));
    }
}
