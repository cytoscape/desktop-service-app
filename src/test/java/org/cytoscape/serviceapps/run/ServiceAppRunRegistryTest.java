package org.cytoscape.serviceapps.run;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.cytoscape.serviceapps.client.JdkHttpTransport;
import org.cytoscape.serviceapps.client.ServiceAppClient;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.FixtureServerExtension;
import org.cytoscape.serviceapps.model.TaskStatus;
import org.cytoscape.serviceapps.render.LayoutsHandler;
import org.cytoscape.serviceapps.render.RenderServiceAppUI;
import org.cytoscape.serviceapps.request.RequestBuilder;
import org.cytoscape.serviceapps.request.ServiceInput;
import org.cytoscape.serviceapps.testsupport.ContractNetwork;
import org.cytoscape.serviceapps.testsupport.FakeCx2Support;
import org.cytoscape.serviceapps.testsupport.TestNetworks;
import org.cytoscape.view.model.CyNetworkViewManager;

import static org.cytoscape.serviceapps.run.ServiceAppRunTest.waitFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Background runs and their Task Status records, as {@code task call} will use them. */
@ExtendWith(FixtureServerExtension.class)
class ServiceAppRunRegistryTest {

    private static final RunSettings FAST =
            new RunSettings(Duration.ofMillis(10), Duration.ofSeconds(20));

    private final ContractNetwork target = new ContractNetwork("target");
    private final CyNetworkViewManager viewManager = mock(CyNetworkViewManager.class);
    private final RequestBuilder requests =
            new RequestBuilder(
                    new FakeCx2Support(
                            TestNetworks.support().getNetworkManager(),
                            viewManager,
                            ContractNetwork.views().getNetworkViewFactory()));
    private final RenderServiceAppUI renderer =
            new RenderServiceAppUI(List.of(new LayoutsHandler(viewManager)));
    private final ActiveRuns active = new ActiveRuns();
    private final ServiceAppRunRegistry registry = new ServiceAppRunRegistry(active);

    {
        when(viewManager.getNetworkViews(target.network)).thenReturn(List.of(target.view));
    }

    @AfterEach
    void stop() {
        registry.shutdown();
    }

    private String start(FixtureServer fixture, String scenario) {
        return registry.start(
                listener ->
                        new ServiceAppRun(
                                new ServiceAppRun.Plan(
                                        scenario,
                                        TestNetworks.scenario(scenario),
                                        new ServiceInput(Map.of("Greeting", "hi"), Map.of()),
                                        target.network,
                                        target.view),
                                new ServiceAppClient(
                                        fixture.url(scenario),
                                        new JdkHttpTransport(
                                                Duration.ofSeconds(5), Duration.ofSeconds(10))),
                                requests,
                                renderer,
                                () -> FAST,
                                active,
                                listener));
    }

    private String status(String id) {
        return registry.status(id).map(s -> s.status).orElse("unknown");
    }

    @Test
    void aRunEndsCompleteWithItsSummary(FixtureServer fixture) throws Exception {
        String id = start(fixture, "failure-slow");

        waitFor(() -> TaskStatus.COMPLETE.equals(status(id)));

        TaskStatus state = registry.status(id).orElseThrow();
        assertEquals(id, state.id);
        assertEquals(100.0, state.progress);
        assertTrue(state.message.startsWith("Applied updateLayouts"), state.message);
        assertNull(state.result);
    }

    @Test
    void aFailedRunKeepsTheReason(FixtureServer fixture) throws Exception {
        String id = start(fixture, "failure-failed");

        waitFor(() -> TaskStatus.FAILED.equals(status(id)));

        assertEquals(
                "failure-failed reported a failure: Algorithm failed: bad input",
                registry.status(id).orElseThrow().message);
    }

    @Test
    void aRunInProgressIsProcessingUntilCancelled(FixtureServer fixture) throws Exception {
        String id = start(fixture, "failure-never-complete");
        waitFor(() -> TaskStatus.PROCESSING.equals(status(id)));

        active.cancelApp("failure-never-complete");

        waitFor(() -> TaskStatus.FAILED.equals(status(id)));
        assertEquals("Cancelled", registry.status(id).orElseThrow().message);
        waitFor(() -> fixture.liveTaskIds().isEmpty());
    }

    @Test
    void concurrentRunsGetTheirOwnIds(FixtureServer fixture) throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(start(fixture, "failure-slow"));
        }

        assertEquals(5, new HashSet<>(ids).size());
        for (String id : ids) {
            waitFor(() -> TaskStatus.COMPLETE.equals(status(id)));
        }
    }

    @Test
    void anUnknownIdHasNoStatus() {
        assertTrue(registry.status("nope").isEmpty());
    }

    @Test
    void onlyTheLast100RunsAreKept(FixtureServer fixture) throws Exception {
        String first = start(fixture, "failure-submit-500");
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < ServiceAppRunRegistry.KEPT_RUNS; i++) {
            rest.add(start(fixture, "failure-submit-500"));
        }

        assertTrue(registry.status(first).isEmpty());
        for (String id : rest) {
            waitFor(() -> TaskStatus.FAILED.equals(status(id)));
        }
    }
}
