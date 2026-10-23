package org.cytoscape.serviceapps.desktop;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.ScenarioCatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code serviceapps} commands in a real Cytoscape, called over CyREST the way the App Store
 * page (GET) and automation clients (POST, JSON) call them. Cytoscape in the container reaches the
 * fixture service app on the test host.
 */
@ExtendWith(DesktopExtension.class)
class ServiceAppCommandsIT {

    private static final String NS = DesktopTestSupport.NAMESPACE;

    private final CyRest cyrest;
    private final FixtureServer fixture;

    ServiceAppCommandsIT(DesktopContainer desktop, FixtureServer fixture) {
        this.cyrest = desktop.cyrest();
        this.fixture = fixture;
    }

    @BeforeEach
    void startEmpty() throws IOException {
        DesktopTestSupport.uninstallAll(cyrest);
    }

    private Map<String, String> installArgs(String version, String scenario) {
        return Map.of(
                "name",
                "Hello World",
                "version",
                version,
                "url",
                DesktopExtension.containerUrl(fixture, scenario));
    }

    @Test
    void installFromTheStorePageStyleGetCall() throws IOException {
        String reply = cyrest.get(NS, "install", installArgs("1.0", ScenarioCatalog.HELLO));

        assertTrue(reply.contains("installed Hello World 1.0"), reply);
        JsonArray apps = DesktopTestSupport.list(cyrest);
        assertEquals(1, apps.size());
        JsonObject app = apps.get(0).getAsJsonObject();
        assertEquals("Hello World", app.get("name").getAsString());
        assertEquals("1.0", app.get("version").getAsString());
        assertEquals(
                DesktopExtension.containerUrl(fixture, ScenarioCatalog.HELLO),
                app.get("url").getAsString());
        assertTrue(app.get("reachable").getAsBoolean());
    }

    /** Desktop accepts install with only url=; name and version come from the service. */
    @Test
    void installWithOnlyAUrl() throws IOException {
        String url = DesktopExtension.containerUrl(fixture, ScenarioCatalog.HELLO);

        String reply = cyrest.get(NS, "install", Map.of("url", url));
        CyRest.Reply json = cyrest.post(NS, "install", Map.of("url", url));

        assertTrue(reply.contains("installed Hello World 1.0"), reply);
        assertTrue(json.ok(), json.toString());
        assertEquals("Hello World", json.data().getAsJsonObject().get("name").getAsString());
        assertEquals("1.0", json.data().getAsJsonObject().get("version").getAsString());
        JsonArray apps = DesktopTestSupport.list(cyrest);
        assertEquals(1, apps.size());
        assertEquals("Hello World", apps.get(0).getAsJsonObject().get("name").getAsString());
    }

    /** A name given is still checked, with or without a version. */
    @Test
    void installRefusesANameMismatchWithoutAVersion() throws IOException {
        CyRest.Reply reply =
                cyrest.post(
                        NS,
                        "install",
                        Map.of(
                                "name",
                                "helloworld",
                                "url",
                                DesktopExtension.containerUrl(fixture, ScenarioCatalog.HELLO)));

        assertFalse(reply.ok(), reply.toString());
        assertTrue(
                reply.toString().contains("reports name 'Hello World', not 'helloworld'"),
                reply.toString());
        assertEquals(0, DesktopTestSupport.list(cyrest).size());
    }

    /**
     * What the App Store page gets from a GET: CyREST wraps each output line in HTML and adds a
     * "Finished" line, so the JSON is the first line once the tags are removed.
     */
    @Test
    void listOverGetIsJsonOnItsFirstLine() throws IOException {
        cyrest.post(NS, "install", installArgs("1.0", ScenarioCatalog.HELLO));

        String reply = cyrest.get(NS, "list", Map.of());
        JsonArray apps =
                JsonParser.parseString(reply.lines().findFirst().orElse("")).getAsJsonArray();

        assertEquals("Hello World", apps.get(0).getAsJsonObject().get("name").getAsString());
        assertTrue(reply.lines().anyMatch(line -> line.strip().equals("Finished")), reply);
    }

    @Test
    void installJsonReply() throws IOException {
        CyRest.Reply reply = cyrest.post(NS, "install", installArgs("1.0", ScenarioCatalog.HELLO));

        assertTrue(reply.ok(), reply.toString());
        JsonObject data = reply.data().getAsJsonObject();
        assertEquals("installed", data.get("status").getAsString());
        assertEquals("Hello World", data.get("name").getAsString());
        assertEquals("1.0", data.get("version").getAsString());
    }

    @Test
    void installRefusesAVersionMismatch() throws IOException {
        CyRest.Reply reply = cyrest.post(NS, "install", installArgs("9.9", ScenarioCatalog.HELLO));

        assertFalse(reply.ok(), reply.toString());
        assertTrue(reply.errors().toString().contains("version '1.0'"), reply.toString());
        assertEquals(0, DesktopTestSupport.list(cyrest).size());
    }

    @Test
    void installReportsAnUnreachableService() throws IOException {
        Map<String, String> args =
                Map.of(
                        "name",
                        "Hello World",
                        "version",
                        "1.0",
                        "url",
                        "http://" + DesktopContainer.HOST_ALIAS + ":1/hello");

        CyRest.Reply reply = cyrest.post(NS, "install", args);

        assertFalse(reply.ok(), reply.toString());
        assertTrue(reply.errors().toString().contains("could not reach"), reply.toString());
    }

    @Test
    void anUnreachableUrlOnlyInstallNamesTheUrl() throws IOException {
        String url = "http://" + DesktopContainer.HOST_ALIAS + ":1/hello";

        CyRest.Reply reply = cyrest.post(NS, "install", Map.of("url", url));

        assertFalse(reply.ok(), reply.toString());
        assertTrue(
                reply.errors().toString().contains("could not install the service app at " + url),
                reply.toString());
    }

    @Test
    void aUrlOnlyInstallUpgradesToTheVersionTheServiceReports() throws IOException {
        cyrest.post(NS, "install", installArgs("1.0", ScenarioCatalog.HELLO));

        CyRest.Reply reply =
                cyrest.post(
                        NS,
                        "install",
                        Map.of(
                                "url",
                                DesktopExtension.containerUrl(fixture, ScenarioCatalog.HELLO_V2)));

        assertTrue(reply.ok(), reply.toString());
        JsonArray apps = DesktopTestSupport.list(cyrest);
        assertEquals(1, apps.size());
        assertEquals("Hello World", apps.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("2.0", apps.get(0).getAsJsonObject().get("version").getAsString());
    }

    @Test
    void installingTheNextVersionReplacesTheApp() throws IOException {
        cyrest.post(NS, "install", installArgs("1.0", ScenarioCatalog.HELLO));

        CyRest.Reply reply =
                cyrest.post(NS, "install", installArgs("2.0", ScenarioCatalog.HELLO_V2));

        assertTrue(reply.ok(), reply.toString());
        JsonArray apps = DesktopTestSupport.list(cyrest);
        assertEquals(1, apps.size());
        assertEquals("2.0", apps.get(0).getAsJsonObject().get("version").getAsString());
    }

    @Test
    void statusChecksTheServiceLive() throws IOException {
        cyrest.post(NS, "install", installArgs("1.0", ScenarioCatalog.HELLO));

        CyRest.Reply reply = cyrest.post(NS, "status", Map.of("name", "Hello World"));

        assertTrue(reply.ok(), reply.toString());
        JsonObject data = reply.data().getAsJsonObject();
        assertTrue(data.get("reachable").getAsBoolean());
        assertEquals(200, data.get("httpStatus").getAsInt());
        assertEquals("ok", data.get("serviceStatus").getAsString());
    }

    @Test
    void uninstallFromTheStorePageStyleGetCall() throws IOException {
        cyrest.post(NS, "install", installArgs("1.0", ScenarioCatalog.HELLO));

        String reply = cyrest.get(NS, "uninstall", Map.of("name", "Hello World"));

        assertTrue(reply.contains("uninstalled Hello World"), reply);
        assertEquals(0, DesktopTestSupport.list(cyrest).size());
    }

    @Test
    void uninstallingAnUnknownAppIsAnError() throws IOException {
        CyRest.Reply reply = cyrest.post(NS, "uninstall", Map.of("name", "Never Installed"));

        assertFalse(reply.ok(), reply.toString());
        assertTrue(reply.errors().toString().contains("Never Installed"), reply.toString());
    }
}
