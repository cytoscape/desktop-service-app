package org.cytoscape.serviceapps.command;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.serviceapps.call.ServiceInputParser;
import org.cytoscape.serviceapps.call.UiTargetResolver;
import org.cytoscape.serviceapps.client.JdkHttpTransport;
import org.cytoscape.serviceapps.client.ServiceAppClient;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.FixtureServerExtension;
import org.cytoscape.serviceapps.form.FormSpecBuilder;
import org.cytoscape.serviceapps.model.ResultAction;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.serviceapps.render.RenderServiceAppUI;
import org.cytoscape.serviceapps.request.RequestBuilder;
import org.cytoscape.serviceapps.run.ActiveRuns;
import org.cytoscape.serviceapps.run.RunSettings;
import org.cytoscape.serviceapps.run.ServiceAppRunFactory;
import org.cytoscape.serviceapps.run.ServiceAppRunRegistry;
import org.cytoscape.serviceapps.testsupport.ContractNetwork;
import org.cytoscape.serviceapps.testsupport.FakeCx2Support;
import org.cytoscape.serviceapps.testsupport.TestNetworks;
import org.cytoscape.util.swing.OpenBrowser;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.work.TaskMonitor;

import static org.cytoscape.serviceapps.command.CommandResults.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** describe, task call and task status in the JVM, against the fixture service. */
@ExtendWith(FixtureServerExtension.class)
class TaskCommandsTest {

    @TempDir Path dir;

    private final ContractNetwork target = new ContractNetwork("target");
    private final CyNetworkManager networks = mock(CyNetworkManager.class);
    private final CyNetworkViewManager views = mock(CyNetworkViewManager.class);
    private final CyApplicationManager applications = mock(CyApplicationManager.class);
    private final FakeCx2Support cx2 =
            new FakeCx2Support(
                    TestNetworks.support().getNetworkManager(),
                    views,
                    ContractNetwork.views().getNetworkViewFactory());

    private FixtureServer fixture;
    private ServiceAppRegistry registry;
    private ServiceAppRunRegistry runs;
    private ServiceAppRunFactory runFactory;

    @BeforeEach
    void setUp(FixtureServer fixture) {
        this.fixture = fixture;
        when(networks.getNetwork(target.network.getSUID())).thenReturn(target.network);
        when(views.getNetworkViews(target.network)).thenReturn(List.of(target.view));
        when(views.getNetworkViewSet()).thenReturn(Set.of(target.view));
        registry = ServiceAppRegistry.inDirectory(dir);
        ActiveRuns active = new ActiveRuns();
        runs = new ServiceAppRunRegistry(active);
        runFactory =
                new ServiceAppRunFactory(
                        registry,
                        url ->
                                new ServiceAppClient(
                                        url,
                                        new JdkHttpTransport(
                                                Duration.ofSeconds(5), Duration.ofSeconds(10))),
                        new RequestBuilder(cx2),
                        RenderServiceAppUI.standard(
                                views, mock(OpenBrowser.class), cx2, applications),
                        () -> new RunSettings(Duration.ofMillis(10), Duration.ofSeconds(20)),
                        active);
    }

    @AfterEach
    void stop() {
        runs.shutdown();
    }

    private String install(String scenario) {
        return TestNetworks.install(registry, fixture, scenario);
    }

    private String call(String app, String serviceInput, String uiInput) {
        TaskCallCommandTask task =
                new TaskCallCommandTask(
                        registry,
                        new UiTargetResolver(networks, views, applications),
                        new ServiceInputParser(new FormSpecBuilder()),
                        runFactory,
                        runs,
                        cx2);
        task.app = app;
        task.serviceInput = serviceInput;
        task.uiInput = uiInput;
        task.run(mock(TaskMonitor.class));
        return json(task).get("task_id").getAsString();
    }

    private JsonObject status(String taskId) {
        TaskStatusCommandTask task = new TaskStatusCommandTask(runs);
        task.task_id = taskId;
        task.run(mock(TaskMonitor.class));
        return json(task);
    }

    private JsonObject finished(String taskId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            JsonObject status = status(taskId);
            String state = status.get("status").getAsString();
            if (state.equals("complete") || state.equals("failed")) {
                return status;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("task " + taskId + " didn't finish");
    }

    private String uiInput() {
        return "{\"networkId\":" + target.network.getSUID() + "}";
    }

    private double x(long node) {
        return target.view
                .getNodeView(target.node(node))
                .getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION);
    }

    private IllegalArgumentException rejected(String app, String serviceInput, String uiInput) {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class, () -> call(app, serviceInput, uiInput));
        assertTrue(
                fixture.requests().stream().noneMatch(r -> r.method().equals("POST")),
                "nothing was sent");
        return e;
    }

    @Test
    void aCallRunsInTheBackgroundAndCompletesOnlyAfterTheResultIsApplied() throws Exception {
        String app = install("result-update-layouts");

        String taskId = call(app, "{\"parameters\":{\"Greeting\":\"hi\"}}", uiInput());
        JsonObject status = finished(taskId);

        assertEquals("complete", status.get("status").getAsString());
        assertEquals(100.0, status.get("progress").getAsDouble());
        assertEquals("Applied updateLayouts", status.get("message").getAsString());
        assertTrue(status.get("result").isJsonNull());
        assertEquals(Set.of("progress", "message", "status", "result"), status.keySet());
        assertEquals(25.2, x(7), "applied before complete was reported");
        String body =
                fixture.requests().stream()
                        .filter(r -> r.method().equals("POST"))
                        .findFirst()
                        .orElseThrow()
                        .body();
        assertEquals(
                "hi",
                JsonParser.parseString(body)
                        .getAsJsonObject()
                        .getAsJsonObject("parameters")
                        .get("Greeting")
                        .getAsString());
    }

    @Test
    void omittedUiInputUsesTheActiveNetworkAndEveryView() throws Exception {
        when(applications.getCurrentNetwork()).thenReturn(target.network);
        String app = install("result-update-layouts");

        assertEquals("complete", finished(call(app, null, null)).get("status").getAsString());
        assertEquals(25.2, x(7));
    }

    @Test
    void aStaleUnreachableFlagDoesNotBlockACall() throws Exception {
        String app = install("result-update-layouts");
        registry.setReachable(app, false);

        assertEquals("complete", finished(call(app, null, uiInput())).get("status").getAsString());
    }

    @Test
    void theDialogsRememberedValuesAreNotTouched() throws Exception {
        String app = install("result-update-layouts");
        JsonObject remembered =
                JsonParser.parseString("{\"parameters\":{\"Greeting\":\"from the dialog\"}}")
                        .getAsJsonObject();
        registry.saveLastValues(app, remembered);

        finished(call(app, "{\"parameters\":{\"Greeting\":\"hi\"}}", uiInput()));

        assertEquals(remembered, registry.getLastValues(app));
    }

    @Test
    void aFailedServiceTaskReportsFailedWithTheServiceMessage() throws Exception {
        JsonObject status = finished(call(install("failure-failed"), null, uiInput()));

        assertEquals("failed", status.get("status").getAsString());
        assertEquals(
                "Service task failed: Algorithm failed: bad input",
                status.get("message").getAsString());
        assertEquals(Set.of("progress", "message", "status", "result"), status.keySet());
        assertTrue(status.get("result").isJsonNull());
    }

    @Test
    void aFailedRenderReportsFailedWithTheRenderingMessage() throws Exception {
        JsonObject status = finished(call(install("result-fail-fast"), null, uiInput()));

        assertEquals("failed", status.get("status").getAsString());
        assertTrue(
                status.get("message")
                        .getAsString()
                        .startsWith("Rendering failed at updateTables (2 of 3)"),
                status.get("message").getAsString());
    }

    @Test
    void aMissingTargetFailsTheTaskNamingTheAction() throws Exception {
        JsonObject status = finished(call(install("input-none"), null, null));

        assertEquals("failed", status.get("status").getAsString());
        assertEquals(
                "Rendering failed: the result asks to updateLayouts, which needs a network, and no"
                        + " network was targeted. Nothing was applied.",
                status.get("message").getAsString());
    }

    @Test
    void aNetworkAppWithNoTargetIsRejectedBeforeAnythingIsSent() {
        String app = install("hello");

        assertEquals(
                app
                        + " works on a network: pass uiInput {\"networkId\":<SUID>} or make a network active.",
                rejected(app, null, null).getMessage());
    }

    @Test
    void anUnknownIdIsRejectedBeforeAnythingIsSent() {
        IllegalArgumentException e = rejected(install("hello"), null, "{\"networkId\":424242}");

        assertEquals("uiInput was rejected: networkId: no network has SUID 424242", e.getMessage());
    }

    @Test
    void invalidServiceInputIsRejectedNamingTheField() {
        IllegalArgumentException e =
                rejected(
                        install("param-text-number"),
                        "{\"parameters\":{\"Resolution\":7}}",
                        uiInput());

        assertEquals(
                "serviceInput was rejected: Resolution: Must be a number from 0 to 1",
                e.getMessage());
    }

    @Test
    void anObjectPassedInsteadOfAJsonStringIsExplained() {
        IllegalArgumentException e =
                rejected(install("hello"), "{parameters={Greeting=hi}}", uiInput());

        assertTrue(
                e.getMessage().startsWith("serviceInput must be passed as a JSON string"),
                e.getMessage());
    }

    @Test
    void anUnsupportedAppIsRejectedWithItsReason() {
        IllegalArgumentException e = rejected(install("unsupported-file"), null, uiInput());

        assertTrue(
                e.getMessage()
                        .startsWith(
                                "Fixture unsupported-file can't run in Cytoscape Desktop: it uses the parameter type 'file'"),
                e.getMessage());
    }

    @Test
    void aSelectedNodesAppNeedsASelectedNode() {
        ServiceMetadata meta = TestNetworks.scenario("result-update-layouts");
        meta.name = "Selected nodes";
        meta.serviceInputDefinition.type = "node";
        meta.serviceInputDefinition.scope = "selected";
        meta.serviceInputDefinition.inputNetwork = null;
        registry.install(meta.name, meta.version, fixture.url("result-update-layouts"), meta);

        IllegalArgumentException e = rejected("Selected nodes", null, uiInput());

        assertTrue(e.getMessage().startsWith("Select at least one node"), e.getMessage());
    }

    @Test
    void anUnknownAppIsRejected() {
        assertEquals(
                "no service app named 'Nope' is installed",
                rejected("Nope", null, null).getMessage());
    }

    @Test
    void anUnknownTaskIdIsAnError() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> status("nope"));

        assertEquals(
                "no task with task_id 'nope': it is unknown, or older than the last 100 runs",
                e.getMessage());
    }

    @Test
    void describeReturnsTheMetadataAndBothSchemas() {
        String app = install("param-text-number");
        DescribeCommandTask task = new DescribeCommandTask(registry, cx2);
        task.name = app;

        task.run(mock(TaskMonitor.class));

        JsonObject json = json(task);
        assertEquals(app, json.getAsJsonObject("metadata").get("name").getAsString());
        assertEquals(
                "number",
                json.getAsJsonObject("serviceInputSchema")
                        .getAsJsonObject("properties")
                        .getAsJsonObject("parameters")
                        .getAsJsonObject("properties")
                        .getAsJsonObject("Resolution")
                        .get("type")
                        .getAsString());
        assertTrue(
                json.getAsJsonObject("uiInputSchema")
                        .getAsJsonObject("properties")
                        .has("networkViewId"));
        assertFalse(json.has("unsupportedReason"));
    }

    @Test
    void describeGivesEachDeclaredResultActionsEffectInOrder() {
        String app = install("param-text-number");
        ServiceMetadata meta = registry.requireMeta(app);
        meta.cyWebActions =
                List.of(
                        ResultAction.UPDATE_TABLES,
                        ResultAction.UPDATE_LAYOUTS,
                        ResultAction.OPEN_URL,
                        "somethingNew");
        registry.updateMeta(app, meta.version, meta);
        DescribeCommandTask task = new DescribeCommandTask(registry, cx2);
        task.name = app;

        task.run(mock(TaskMonitor.class));

        JsonArray actions = json(task).getAsJsonArray("resultActions");
        assertEquals(4, actions.size());
        JsonObject tables = actions.get(0).getAsJsonObject();
        assertEquals("updateTables", tables.get("action").getAsString());
        assertEquals(
                "Adds or fills in node or edge columns of the target network; rows for IDs not in"
                        + " the network are skipped.",
                tables.get("effect").getAsString());
        assertEquals(JsonParser.parseString("[\"networkId\"]"), tables.get("targets"));
        assertEquals(
                JsonParser.parseString("[\"networkId\",\"networkViewId\"]"),
                actions.get(1).getAsJsonObject().get("targets"));
        assertEquals(new JsonArray(), actions.get(2).getAsJsonObject().get("targets"));
        JsonObject unknown = actions.get(3).getAsJsonObject();
        assertEquals("somethingNew", unknown.get("action").getAsString());
        assertTrue(
                unknown.get("effect")
                        .getAsString()
                        .startsWith("Not a result action Desktop knows"));
    }

    @Test
    void describeOfAnAppDeclaringNoActionsHasNoResultActions() {
        String app = install("param-text-number");
        ServiceMetadata meta = registry.requireMeta(app);
        meta.cyWebActions = null;
        registry.updateMeta(app, meta.version, meta);
        DescribeCommandTask task = new DescribeCommandTask(registry, cx2);
        task.name = app;

        task.run(mock(TaskMonitor.class));

        assertEquals(new JsonArray(), json(task).getAsJsonArray("resultActions"));
    }

    @Test
    void describesExampleHasTheShapeOfARealReply() {
        DescribeCommandTask task = new DescribeCommandTask(registry, cx2);
        task.name = install("param-text-number");
        task.run(mock(TaskMonitor.class));

        JsonObject example =
                JsonParser.parseString(DescribeCommandTask.EXAMPLE_JSON).getAsJsonObject();
        assertEquals(json(task).keySet(), example.keySet());
        assertEquals(
                json(task).getAsJsonArray("resultActions").get(0).getAsJsonObject().keySet(),
                example.getAsJsonArray("resultActions").get(0).getAsJsonObject().keySet());
    }

    @Test
    void describeGivesTheReasonForAnUnsupportedApp() {
        DescribeCommandTask task = new DescribeCommandTask(registry, cx2);
        task.name = install("unsupported-file");

        task.run(mock(TaskMonitor.class));

        assertTrue(json(task).get("unsupportedReason").getAsString().contains("'file'"));
    }

    @Test
    void describeRejectsAnUnknownApp() {
        DescribeCommandTask task = new DescribeCommandTask(registry, cx2);
        task.name = "Nope";

        assertEquals(
                "no service app named 'Nope' is installed",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> task.run(mock(TaskMonitor.class)))
                        .getMessage());
    }
}
