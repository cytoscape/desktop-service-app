package org.cytoscape.serviceapps.command;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.serviceapps.client.FakeTransport;
import org.cytoscape.serviceapps.client.ServiceAppClient;
import org.cytoscape.serviceapps.client.ServiceAppClientFactory;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.InstalledServiceApp;
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

    /** One way of passing name= or version=, against a service reporting Hello World 1.0. */
    private enum Arg {
        ABSENT(null),
        BLANK("  "),
        MATCHING(null),
        MISMATCHED(null);

        final String fixed;

        Arg(String fixed) {
            this.fixed = fixed;
        }

        String value(String matching, String mismatched) {
            return switch (this) {
                case ABSENT, BLANK -> fixed;
                case MATCHING -> matching;
                case MISMATCHED -> mismatched;
            };
        }
    }

    static Stream<Arguments> everyNameAndVersionCombination() {
        List<Arguments> cases = new ArrayList<>();
        for (Arg name : Arg.values()) {
            for (Arg version : Arg.values()) {
                cases.add(Arguments.of(name, version));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "name {0}, version {1}")
    @MethodSource("everyNameAndVersionCombination")
    void installChecksNameAndVersionOnlyWhenGiven(Arg name, Arg version) {
        transport.reply(200, "{\"name\":\"Hello World\",\"version\":\"1.0\"}");
        InstallCommandTask task = new InstallCommandTask(registry, clients);
        task.url = URL;
        task.name = name.value("Hello World", "Hello");
        task.version = version.value("1.0", "2.0");

        if (name == Arg.MISMATCHED || version == Arg.MISMATCHED) {
            IllegalStateException e =
                    assertThrows(IllegalStateException.class, () -> task.run(monitor));

            String expected =
                    "the service at "
                            + URL
                            + " "
                            + String.join(
                                    " and ",
                                    Stream.of(
                                                    name == Arg.MISMATCHED
                                                            ? "reports name 'Hello World', not 'Hello'"
                                                            : null,
                                                    version == Arg.MISMATCHED
                                                            ? "reports version '1.0', not '2.0'"
                                                            : null)
                                            .filter(Objects::nonNull)
                                            .toList())
                            + "; nothing was installed";
            assertEquals(expected, e.getMessage());
            assertTrue(registry.list().isEmpty(), "nothing installed");
        } else {
            task.run(monitor);

            // Absent, blank and matching all install what the service reports.
            assertEquals("installed Hello World 1.0", task.getResults(String.class));
            JsonObject reply = json(task);
            assertEquals("installed", reply.get("status").getAsString());
            assertEquals("Hello World", reply.get("name").getAsString());
            assertEquals("1.0", reply.get("version").getAsString());
            InstalledServiceApp app = registry.get("Hello World").orElseThrow();
            assertEquals("1.0", app.version());
            assertEquals(URL, app.url());
            assertEquals(1, registry.list().size());
        }
    }

    @Test
    void installWithOnlyAUrlUsesTheNameAndVersionTheServiceReports() {
        transport.reply(200, "{\"name\":\"Update tables example\",\"version\":\"0.9.0\"}");
        InstallCommandTask task = new InstallCommandTask(registry, clients);
        task.url = "https://cd.example/v1/updatetablesexample/";

        task.run(monitor);

        assertEquals("installed Update tables example 0.9.0", task.getResults(String.class));
        InstalledServiceApp app = registry.get("Update tables example").orElseThrow();
        assertEquals("0.9.0", app.version());
        assertEquals("https://cd.example/v1/updatetablesexample", app.url());
        assertEquals("GET", transport.last().method());
    }

    @Test
    void aServiceThatReportsNoNameIsRefusedWithOrWithoutName() {
        for (String name : new String[] {null, "Hello World"}) {
            transport.reply(200, "{\"version\":\"1.0\"}");
            InstallCommandTask task = new InstallCommandTask(registry, clients);
            task.url = URL;
            task.name = name;

            IllegalStateException e =
                    assertThrows(IllegalStateException.class, () -> task.run(monitor), name);

            assertEquals(
                    "the service at " + URL + " reports no name; nothing was installed",
                    e.getMessage());
            assertTrue(registry.list().isEmpty());
        }
    }

    @Test
    void aServiceThatReportsNoVersionIsRefusedWithOrWithoutVersion() {
        for (String version : new String[] {null, "1.0"}) {
            transport.reply(200, "{\"name\":\"Hello World\",\"version\":\" \"}");
            InstallCommandTask task = new InstallCommandTask(registry, clients);
            task.url = URL;
            task.version = version;

            IllegalStateException e =
                    assertThrows(IllegalStateException.class, () -> task.run(monitor), version);

            assertEquals(
                    "the service at " + URL + " reports no version; nothing was installed",
                    e.getMessage());
            assertTrue(registry.list().isEmpty());
        }
    }

    @Test
    void installReportsAnUnreachableService() {
        transport.fail(new ConnectException("Connection refused"));
        InstallCommandTask task = new InstallCommandTask(registry, clients);
        task.name = "Hello World";
        task.url = URL;

        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> task.run(monitor));

        assertTrue(e.getMessage().contains("could not install Hello World"), e.getMessage());
        assertTrue(e.getMessage().contains("Connection refused"), e.getMessage());
        assertTrue(registry.list().isEmpty());
    }

    @Test
    void anUnreachableServiceGivenOnlyByUrlIsNamedByItsUrl() {
        transport.fail(new ConnectException("Connection refused"));
        InstallCommandTask task = new InstallCommandTask(registry, clients);
        task.url = URL;

        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> task.run(monitor));

        assertTrue(
                e.getMessage().startsWith("could not install the service app at " + URL + ": "),
                e.getMessage());
    }

    @Test
    void installRequiresAnHttpUrlAndNothingElse() {
        InstallCommandTask task = new InstallCommandTask(registry, clients);
        task.name = "Hello World";
        task.version = "1.0";
        assertEquals(
                "url= is required",
                assertThrows(IllegalArgumentException.class, () -> task.run(monitor)).getMessage());
        task.url = " ";
        assertThrows(IllegalArgumentException.class, () -> task.run(monitor));
        task.url = "ftp://example.org/x";
        assertThrows(IllegalArgumentException.class, () -> task.run(monitor));
        assertTrue(transport.sent.isEmpty(), "nothing was fetched");
    }

    @Test
    void installWithOnlyAUrlUpgradesTheAppTheServiceNames() {
        registry.install(
                "Hello World", "1.0", "https://old.example/hello", meta("Hello World", "1.0"));
        transport.reply(200, "{\"name\":\"Hello World\",\"version\":\"2.0\"}");
        InstallCommandTask task = new InstallCommandTask(registry, clients);
        task.url = URL;

        task.run(monitor);

        assertEquals(1, registry.list().size());
        assertEquals("2.0", registry.get("Hello World").orElseThrow().version());
        assertEquals(URL, registry.get("Hello World").orElseThrow().url());
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
