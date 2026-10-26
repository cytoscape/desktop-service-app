package org.cytoscape.serviceapps.contract;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.app.AppSupport;
import org.cytoscape.serviceapps.call.ResultActionEffects;
import org.cytoscape.serviceapps.call.ServiceInputSchema;
import org.cytoscape.serviceapps.command.Commands;
import org.cytoscape.serviceapps.contract.ContractHarness.Outcome;
import org.cytoscape.serviceapps.contract.ContractHarness.Ui;
import org.cytoscape.serviceapps.desktop.CyRest;
import org.cytoscape.serviceapps.desktop.DesktopContainer;
import org.cytoscape.serviceapps.desktop.DesktopExtension;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.ScenarioCatalog;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.testsupport.TestNetworks;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** describe, task call and task status over CyREST, against real Cytoscape in Docker. */
@ExtendWith(DesktopExtension.class)
class ServiceAppTaskCommandsIT {

    private final DesktopContainer desktop;
    private final FixtureServer fixture;
    private final CyRest cyrest;
    private final DockerTaskCallHarness h;

    ServiceAppTaskCommandsIT(DesktopContainer desktop, FixtureServer fixture) {
        this.desktop = desktop;
        this.fixture = fixture;
        this.cyrest = desktop.cyrest();
        this.h = new DockerTaskCallHarness(desktop, fixture, false);
    }

    @BeforeEach
    void loadNetworks() {
        h.reset();
    }

    private CyRest.Reply call(String scenario, Map<String, String> extra) throws IOException {
        h.install(scenario);
        Map<String, String> args = new HashMap<>(extra);
        args.put("app", ScenarioCatalog.get(scenario).appName());
        return cyrest.post("serviceapps", "task call", args);
    }

    private String error(CyRest.Reply reply) {
        assertFalse(reply.ok(), "expected a rejection: " + reply);
        return DockerTaskCallHarness.errorMessage(reply);
    }

    private long posts(String scenario) {
        return fixture.requests().stream()
                .filter(r -> r.method().equals("POST") && r.path().equals("/" + scenario))
                .count();
    }

    private String uiInput(String body) {
        return "{" + body + "}";
    }

    @Test
    void describeReturnsTheSameSchemaAsTheDialogRulesForEveryScenario() throws IOException {
        for (String scenario : ScenarioCatalog.all().keySet().stream().sorted().toList()) {
            h.install(scenario);
            CyRest.Reply reply =
                    cyrest.post(
                            "serviceapps",
                            "describe",
                            Map.of("name", ScenarioCatalog.get(scenario).appName()));
            assertTrue(reply.ok(), scenario + ": " + reply);
            JsonObject data = reply.data().getAsJsonObject();
            ServiceMetadata meta = TestNetworks.scenario(scenario);

            assertEquals(ServiceInputSchema.of(meta), data.get("serviceInputSchema"), scenario);
            assertEquals(ResultActionEffects.of(meta), data.get("resultActions"), scenario);
            assertEquals(
                    AppSupport.unsupportedReason(meta, true).orElse(null),
                    data.has("unsupportedReason")
                            ? data.get("unsupportedReason").getAsString()
                            : null,
                    scenario);
            assertEquals(meta.name, data.getAsJsonObject("metadata").get("name").getAsString());
            assertTrue(
                    data.getAsJsonObject("uiInputSchema")
                            .getAsJsonObject("properties")
                            .has("networkViewId"));
        }
    }

    @Test
    void everyCommandsMetadataIsInCyRestsCommandSwagger() throws IOException {
        JsonObject paths =
                cyrest.rest("GET", "/v1/commands/swagger.json", null)
                        .getAsJsonObject()
                        .getAsJsonObject("paths");

        for (var type : Commands.ALL) {
            Commands.Command command = Commands.Command.of(type);
            JsonObject path = paths.getAsJsonObject("/v1/commands/serviceapps/" + command.name());
            assertTrue(path != null, "swagger lists " + command.name());
            JsonObject operation =
                    path.has("post") ? path.getAsJsonObject("post") : path.getAsJsonObject("get");
            assertEquals(command.description(), operation.get("summary").getAsString());
            assertTrue(
                    operation
                            .get("description")
                            .getAsString()
                            .startsWith(command.longDescription()),
                    command.name());
        }
    }

    @Test
    void invalidServiceInputIsRejectedNamingTheFieldAndNothingIsSent() throws IOException {
        long before = posts("param-text-number");

        String message =
                error(
                        call(
                                "param-text-number",
                                Map.of(
                                        "serviceInput",
                                        "{\"parameters\":{\"Resolution\":7}}",
                                        "uiInput",
                                        uiInput("\"networkId\":" + h.targetSuid()))));

        assertTrue(message.contains("Resolution: Must be a number from 0 to 1"), message);
        assertEquals(before, posts("param-text-number"));
    }

    @Test
    void anUnknownIdIsRejectedBeforeAnythingIsSent() throws IOException {
        long before = posts("result-update-layouts");

        String message =
                error(
                        call(
                                "result-update-layouts",
                                Map.of("uiInput", uiInput("\"networkId\":987654"))));

        assertTrue(message.contains("networkId: no network has SUID 987654"), message);
        assertEquals(before, posts("result-update-layouts"));
    }

    @Test
    void aViewOfAnotherNetworkIsRejected() throws IOException {
        long view = h.targetViewSuid(0);

        String message =
                error(
                        call(
                                "result-update-layouts",
                                Map.of(
                                        "uiInput",
                                        uiInput(
                                                "\"networkId\":"
                                                        + h.otherSuid()
                                                        + ",\"networkViewId\":"
                                                        + view))));

        assertTrue(message.contains("is a view of network " + h.targetSuid()), message);
    }

    @Test
    void networkViewIdAloneTargetsThatViewAndItsNetwork() throws IOException {
        h.addTargetView();
        double[] firstBefore = h.position(0, 7);

        Outcome outcome =
                h.await(
                        call(
                                "result-update-layouts",
                                Map.of(
                                        "uiInput",
                                        uiInput("\"networkViewId\":" + h.targetViewSuid(1)))));

        assertTrue(outcome.succeeded(), outcome.message());
        assertArrayEquals(
                new double[] {25.2, -473.6},
                new double[] {h.position(1, 7)[0], h.position(1, 7)[1]});
        assertArrayEquals(firstBefore, h.position(0, 7), "the other view is unchanged");
    }

    @Test
    void anUnsupportedAppIsRejectedWithItsReason() throws IOException {
        String message =
                error(
                        call(
                                "unsupported-file",
                                Map.of("uiInput", uiInput("\"networkId\":" + h.targetSuid()))));

        assertTrue(
                message.contains(
                        "can't run in Cytoscape Desktop: it uses the parameter type 'file'"),
                message);
    }

    @Test
    void aServiceFailureAndARenderFailureEachSayWhich() {
        Outcome service = h.run("failure-failed", Ui.NO_CURRENT_VIEW);
        Outcome render = h.run("result-fail-fast", Ui.NO_CURRENT_VIEW);

        assertFalse(service.succeeded());
        assertEquals("Service task failed: Algorithm failed: bad input", service.message());
        assertFalse(render.succeeded());
        assertTrue(
                render.message().startsWith("Rendering failed at updateTables (2 of 3)"),
                render.message());
    }

    @Test
    void anUnknownTaskIdIsAnError() throws IOException {
        String message =
                error(cyrest.post("serviceapps", "task status", Map.of("task_id", "no-such-task")));

        assertTrue(message.contains("no task with task_id 'no-such-task'"), message);
    }

    @Test
    void jsonArgumentsMustBeStrings() throws IOException {
        // An object form arriving as text, as the MCP Desktop app sends it.
        String message =
                error(
                        call(
                                "hello",
                                Map.of(
                                        "serviceInput",
                                        "{parameters={Greeting=hi}}",
                                        "uiInput",
                                        uiInput("\"networkId\":" + h.targetSuid()))));
        assertTrue(message.contains("serviceInput must be passed as a JSON string"), message);

        // An object in the CyREST body is refused by Desktop before the command runs.
        JsonObject body = new JsonObject();
        body.addProperty("app", ScenarioCatalog.get("hello").appName());
        JsonObject target = new JsonObject();
        target.addProperty("networkId", h.targetSuid());
        body.add("uiInput", target);
        JsonElement reply = cyrest.rest("POST", "/v1/commands/serviceapps/task%20call", body);
        assertNotEquals(
                0, reply.getAsJsonObject().getAsJsonArray("errors").size(), reply.toString());
        assertTrue(reply.toString().contains("uiInput"), reply.toString());
    }
}
