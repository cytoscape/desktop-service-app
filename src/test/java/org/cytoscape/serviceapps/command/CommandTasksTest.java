package org.cytoscape.serviceapps.command;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.serviceapps.client.FakeTransport;
import org.cytoscape.serviceapps.client.ServiceAppClient;
import org.cytoscape.serviceapps.client.ServiceAppClientFactory;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.json.JSONResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CommandTasksTest {

    private static final String URL = "https://example.org/hello";

    @TempDir Path dir;

    private final TaskMonitor monitor = mock(TaskMonitor.class);
    private final FakeTransport transport = new FakeTransport();
    private final ServiceAppClientFactory clients = url -> new ServiceAppClient(url, transport);
    private ServiceAppRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ServiceAppRegistry.inDirectory(dir);
    }

    private static JsonObject json(JsonResultTask task) {
        return JsonParser.parseString(task.getResults(JSONResult.class).getJSON())
                .getAsJsonObject();
    }

    private static ServiceMetadata meta(String name, String version) {
        ServiceMetadata meta = new ServiceMetadata();
        meta.name = name;
        meta.version = version;
        return meta;
    }

    @Test
    void installFetchesMetadataAndSavesTheApp() {
        transport.reply(200, "{\"name\":\"Hello World\",\"version\":\"1.0\"}");
        InstallCommandTask task = new InstallCommandTask(registry, clients);
        task.name = "Hello World";
        task.version = "1.0";
        task.url = URL + "/";

        task.run(monitor);

        assertEquals("installed Hello World 1.0", task.getResults(String.class));
        JsonObject reply = json(task);
        assertEquals("installed", reply.get("status").getAsString());
        assertEquals("Hello World", reply.get("name").getAsString());
        assertEquals("1.0", reply.get("version").getAsString());
        assertEquals(URL, registry.get("Hello World").orElseThrow().url());
        assertEquals("1.0", registry.getMeta("Hello World").orElseThrow().version);
    }

    @Test
    void installRefusesAServiceThatReportsADifferentNameOrVersion() {
        transport.reply(200, "{\"name\":\"Hello World\",\"version\":\"2.0\"}");
        InstallCommandTask task = new InstallCommandTask(registry, clients);
        task.name = "Hello World";
        task.version = "1.0";
        task.url = URL;

        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> task.run(monitor));

        assertTrue(e.getMessage().contains("version '2.0'"), e.getMessage());
        assertTrue(registry.list().isEmpty());
    }

    @Test
    void installReportsAnUnreachableService() {
        transport.fail(new ConnectException("Connection refused"));
        InstallCommandTask task = new InstallCommandTask(registry, clients);
        task.name = "Hello World";
        task.version = "1.0";
        task.url = URL;

        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> task.run(monitor));

        assertTrue(e.getMessage().contains("could not install Hello World"), e.getMessage());
        assertTrue(e.getMessage().contains("Connection refused"), e.getMessage());
        assertTrue(registry.list().isEmpty());
    }

    @Test
    void installRequiresEveryArgumentAndAnHttpUrl() {
        InstallCommandTask task = new InstallCommandTask(registry, clients);
        task.name = "Hello";
        task.version = "1.0";
        assertThrows(IllegalArgumentException.class, () -> task.run(monitor));
        task.url = "ftp://example.org/x";
        assertThrows(IllegalArgumentException.class, () -> task.run(monitor));
        task.url = URL;
        task.version = " ";
        assertThrows(IllegalArgumentException.class, () -> task.run(monitor));
    }

    @Test
    void installTheSameNameReplacesTheApp() {
        registry.install(
                "Hello World", "1.0", "https://old.example/hello", meta("Hello World", "1.0"));
        transport.reply(200, "{\"name\":\"Hello World\",\"version\":\"2.0\"}");
        InstallCommandTask task = new InstallCommandTask(registry, clients);
        task.name = "Hello World";
        task.version = "2.0";
        task.url = URL;

        task.run(monitor);

        assertEquals(1, registry.list().size());
        assertEquals("2.0", registry.get("Hello World").orElseThrow().version());
        assertEquals(URL, registry.get("Hello World").orElseThrow().url());
    }

    @Test
    void listReturnsEveryAppAsJsonInBothForms() {
        registry.install("b", "1.0", "https://b.example", meta("b", "1.0"));
        registry.install("a", "2.0", "https://a.example", meta("a", "2.0"));
        registry.setReachable("b", false);
        ListCommandTask task = new ListCommandTask(registry);

        task.run(monitor);

        JsonArray apps = JsonParser.parseString(task.getResults(String.class)).getAsJsonArray();
        assertEquals(task.getResults(JSONResult.class).getJSON(), task.getResults(String.class));
        assertEquals(2, apps.size());
        JsonObject first = apps.get(0).getAsJsonObject();
        assertEquals("a", first.get("name").getAsString());
        assertEquals("2.0", first.get("version").getAsString());
        assertEquals("https://a.example", first.get("url").getAsString());
        assertTrue(first.get("reachable").getAsBoolean());
        assertFalse(apps.get(1).getAsJsonObject().get("reachable").getAsBoolean());
    }

    @Test
    void listWithNothingInstalledIsAnEmptyArray() {
        ListCommandTask task = new ListCommandTask(registry);

        task.run(monitor);

        assertEquals("[]", task.getResults(String.class));
    }

    @Test
    void uninstallRemovesTheAppAndRejectsUnknownNames() {
        registry.install("Hello", "1.0", URL, meta("Hello", "1.0"));
        UninstallCommandTask task = new UninstallCommandTask(registry);
        task.name = "Hello";

        task.run(monitor);

        assertEquals("uninstalled Hello", task.getResults(String.class));
        assertEquals("uninstalled", json(task).get("status").getAsString());
        assertTrue(registry.list().isEmpty());
        UninstallCommandTask again = new UninstallCommandTask(registry);
        again.name = "Hello";
        assertThrows(IllegalArgumentException.class, () -> again.run(monitor));
    }

    @Test
    void statusReportsAReachableServiceAndSavesIt() {
        registry.install("Hello", "1.0", URL, meta("Hello", "1.0"));
        registry.setReachable("Hello", false);
        transport.reply(200, "{\"status\":\"ok\",\"version\":\"1.0\"}");
        StatusCommandTask task = new StatusCommandTask(registry, clients);
        task.name = "Hello";

        task.run(monitor);

        JsonObject reply = json(task);
        assertEquals("Hello", reply.get("name").getAsString());
        assertTrue(reply.get("reachable").getAsBoolean());
        assertEquals(200, reply.get("httpStatus").getAsInt());
        assertEquals("ok", reply.get("serviceStatus").getAsString());
        assertEquals(URL + "/status", transport.last().uri().toString());
        assertTrue(registry.get("Hello").orElseThrow().reachable());
    }

    @Test
    void statusReportsAnErrorReplyAsUnreachable() {
        registry.install("Hello", "1.0", URL, meta("Hello", "1.0"));
        transport.reply(503, "{\"message\":\"down for maintenance\"}");
        StatusCommandTask task = new StatusCommandTask(registry, clients);
        task.name = "Hello";

        task.run(monitor);

        JsonObject reply = json(task);
        assertFalse(reply.get("reachable").getAsBoolean());
        assertEquals(503, reply.get("httpStatus").getAsInt());
        assertTrue(reply.get("serviceStatus").isJsonNull());
        assertTrue(reply.get("message").getAsString().contains("down for maintenance"));
        assertFalse(registry.get("Hello").orElseThrow().reachable());
    }

    @Test
    void statusReportsNoResponseAsANullHttpStatus() throws IOException {
        registry.install("Hello", "1.0", URL, meta("Hello", "1.0"));
        transport.fail(new ConnectException("Connection refused"));
        StatusCommandTask task = new StatusCommandTask(registry, clients);
        task.name = "Hello";

        task.run(monitor);

        JsonObject reply = json(task);
        assertFalse(reply.get("reachable").getAsBoolean());
        assertTrue(reply.get("httpStatus").isJsonNull());
    }

    @Test
    void statusRejectsAnUnknownName() {
        StatusCommandTask task = new StatusCommandTask(registry, clients);
        task.name = "Nope";

        assertThrows(IllegalArgumentException.class, () -> task.run(monitor));
    }

    @Test
    void resultsAreNullBeforeRunningAndForOtherTypes() {
        ListCommandTask task = new ListCommandTask(registry);
        assertNull(task.getResults(String.class));
        task.run(monitor);
        assertNull(task.getResults(Integer.class));
    }
}
