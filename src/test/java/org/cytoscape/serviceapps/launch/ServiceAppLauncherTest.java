package org.cytoscape.serviceapps.launch;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.google.gson.JsonObject;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.serviceapps.client.JdkHttpTransport;
import org.cytoscape.serviceapps.client.ServiceAppClient;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.FixtureServerExtension;
import org.cytoscape.serviceapps.form.FormSpecBuilder;
import org.cytoscape.serviceapps.form.FormValues;
import org.cytoscape.serviceapps.menu.ServiceAppMenuTaskFactory;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.serviceapps.render.RenderServiceAppUI;
import org.cytoscape.serviceapps.request.RequestBuilder;
import org.cytoscape.serviceapps.run.ActiveRuns;
import org.cytoscape.serviceapps.run.RunSettings;
import org.cytoscape.serviceapps.run.ServiceAppRun;
import org.cytoscape.serviceapps.run.ServiceAppRunFactory;
import org.cytoscape.serviceapps.testsupport.FakeCx2Support;
import org.cytoscape.serviceapps.testsupport.TestNdex;
import org.cytoscape.serviceapps.testsupport.TestNetworks;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.Task;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.TunableValidator.ValidationState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** One launch path for menu entries and Invoke: explain why not, or show the dialog and run. */
@ExtendWith(FixtureServerExtension.class)
class ServiceAppLauncherTest {

    @TempDir Path dir;

    private final TaskManager<?, ?> taskManager = mock(TaskManager.class);
    private final CyApplicationManager applications = mock(CyApplicationManager.class);
    private final FakeCx2Support cx2 =
            new FakeCx2Support(
                    TestNetworks.support().getNetworkManager(),
                    mock(CyNetworkViewManager.class),
                    null);
    private final List<String> warnings = new ArrayList<>();
    private final CyNetwork network = TestNetworks.withEveryColumnType();

    private ServiceAppRegistry registry;
    private ServiceAppLauncher launcher;
    private FixtureServer fixture;

    @BeforeEach
    void setUp(FixtureServer fixture) {
        this.fixture = fixture;
        registry = spy(ServiceAppRegistry.inDirectory(dir));
        ServiceAppRunFactory runs =
                new ServiceAppRunFactory(
                        registry,
                        url ->
                                new ServiceAppClient(
                                        url,
                                        new JdkHttpTransport(
                                                Duration.ofSeconds(2), Duration.ofSeconds(5))),
                        new RequestBuilder(cx2, TestNdex.link()),
                        new RenderServiceAppUI(List.of()),
                        () -> RunSettings.DEFAULTS,
                        new ActiveRuns());
        launcher =
                new ServiceAppLauncher(
                        registry,
                        new FormSpecBuilder(),
                        taskManager,
                        applications,
                        runs,
                        cx2,
                        (title, message) -> warnings.add(title + ": " + message));
    }

    private String install(String scenario) {
        return TestNetworks.install(registry, fixture, scenario);
    }

    private String installAs(String name, String scenario, String type, String scope) {
        ServiceMetadata meta = TestNetworks.scenario(scenario);
        meta.name = name;
        meta.serviceInputDefinition.type = type;
        meta.serviceInputDefinition.scope = scope;
        registry.install(name, meta.version, fixture.url(scenario), meta);
        return name;
    }

    private TaskIterator launched() {
        ArgumentCaptor<TaskIterator> tasks = ArgumentCaptor.forClass(TaskIterator.class);
        verify(taskManager).execute(tasks.capture());
        return tasks.getValue();
    }

    private void select(CyNode node) {
        network.getRow(node).set(CyNetwork.SELECTED, true);
    }

    @Test
    void aNetworkAppWithNoNetworkOpenExplainsInsteadOfShowingTheDialog() {
        String name = install("menu-root-apps");

        launcher.launch(name);

        assertEquals(
                List.of(
                        name
                                + ": "
                                + name
                                + " works on the current network. Open a network first."),
                warnings);
        verify(taskManager, never()).execute(any());
    }

    @Test
    void anNdexUuidAppThatReadsANetworkExplainsWhenNoneIsOpen() {
        String name = install("param-ndex-uuid");

        launcher.launch(name);

        assertEquals(
                List.of(
                        name
                                + ": "
                                + name
                                + " works on the current network. Open a network first."),
                warnings);
        verify(taskManager, never()).execute(any());
    }

    @Test
    void aSelectedNodesAppNeedsASelectedNode() {
        String name = installAs("Nodes", "hello", "node", "selected");
        when(applications.getCurrentNetwork()).thenReturn(network);

        launcher.launch(name);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("Select at least one node"), warnings.get(0));
        verify(taskManager, never()).execute(any());

        select(network.getNodeList().get(0));
        launcher.launch(name);
        assertInstanceOf(ServiceAppRunTask.class, launched().next());
    }

    @Test
    void anUnreachableAppExplains() {
        String name = install("menu-root-apps");
        registry.setReachable(name, false);
        when(applications.getCurrentNetwork()).thenReturn(network);

        launcher.launch(name);

        assertTrue(warnings.get(0).contains("can't be reached right now"), warnings.get(0));
        verify(taskManager, never()).execute(any());
    }

    @Test
    void anAppWithoutInputsStillShowsItsDialogWithNoNetworkOpen() {
        String name = install("input-none");

        launcher.launch(name);

        ServiceAppRunTask task = (ServiceAppRunTask) launched().next();
        assertEquals(name + " 1.0", task.getTitle());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void theDialogStartsFromTheRememberedValues() {
        String name = install("menu-root-apps");
        JsonObject remembered =
                new FormValues(Map.of("Greeting", List.of("hey")), Map.of()).toJson();
        registry.saveLastValues(name, remembered);
        when(applications.getCurrentNetwork()).thenReturn(network);

        launcher.launch(name);

        ServiceAppRunTask task = (ServiceAppRunTask) launched().next();
        assertEquals(List.of("hey"), task.form.values().parameters().get("Greeting"));
        assertEquals(network, task.target());
        verify(registry, atLeastOnce()).getMeta(name);
        verify(registry).getLastValues(name);
    }

    @Test
    void cancellingTheDialogSavesNothingAndStartsNothing() {
        String name = install("menu-root-apps");
        when(applications.getCurrentNetwork()).thenReturn(network);

        TaskIterator tasks = launcher.createTaskIterator(name);
        // Desktop's task manager stops here when the user cancels: the dialog task never runs.

        verify(registry, never()).saveLastValues(eq(name), any());
        assertInstanceOf(ServiceAppRunTask.class, tasks.next());
        assertTrue(!tasks.hasNext(), "the run is only queued by OK");
    }

    @Test
    void okRemembersTheValuesAndQueuesTheRunAfterTheDialog() throws Exception {
        String name = install("menu-root-apps");
        when(applications.getCurrentNetwork()).thenReturn(network);
        TaskIterator tasks = launcher.createTaskIterator(name);
        ServiceAppRunTask dialog = (ServiceAppRunTask) tasks.next();
        dialog.form.setValues(new FormValues(Map.of("Greeting", List.of("bonjour")), Map.of()));

        dialog.run(mock(TaskMonitor.class));

        assertEquals(
                "bonjour",
                registry.getLastValues(name)
                        .getAsJsonObject("parameters")
                        .get("Greeting")
                        .getAsString());
        verify(registry).saveLastValues(eq(name), any());
        Task next = tasks.next();
        assertInstanceOf(ServiceAppRun.class, next);
        assertEquals(name, ((ServiceAppRun) next).appName());
    }

    @Test
    void invalidValuesKeepTheDialogOpenWithTheReason() {
        String name = install("param-text-number");
        when(applications.getCurrentNetwork()).thenReturn(network);
        ServiceAppRunTask dialog = (ServiceAppRunTask) launcher.createTaskIterator(name).next();
        String key = dialog.form.spec().parameters().get(0).key();
        StringBuilder message = new StringBuilder();

        dialog.form.setValues(new FormValues(Map.of(key, List.of("abc")), Map.of()));
        assertEquals(ValidationState.INVALID, dialog.getValidationState(message));
        assertTrue(message.toString().contains("Must be a number from 0 to 1"), message.toString());

        dialog.form.setValues(new FormValues(Map.of(key, List.of("0.25")), Map.of()));
        assertEquals(ValidationState.OK, dialog.getValidationState(new StringBuilder()));
    }

    @Test
    void aMenuEntryClickedWhenTheAppCantRunExplainsWhy() throws Exception {
        String name = install("menu-root-apps");
        ServiceAppMenuTaskFactory entry = new ServiceAppMenuTaskFactory(name, true, launcher);

        Task task = entry.createTaskIterator().next();
        task.run(mock(TaskMonitor.class));

        assertEquals(
                List.of(
                        name
                                + ": "
                                + name
                                + " works on the current network. Open a network first."),
                warnings);
    }

    @Test
    void anUninstalledAppExplains() {
        launcher.launch("Gone");

        assertEquals(List.of("Gone: Gone is no longer installed."), warnings);
    }
}
