package org.cytoscape.serviceapps.run;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.serviceapps.client.HttpTransport;
import org.cytoscape.serviceapps.client.JdkHttpTransport;
import org.cytoscape.serviceapps.client.ServiceAppClient;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.FixtureServerExtension;
import org.cytoscape.serviceapps.model.TaskStatus;
import org.cytoscape.serviceapps.ndex.NdexLink;
import org.cytoscape.serviceapps.render.LayoutsHandler;
import org.cytoscape.serviceapps.render.RenderServiceAppUI;
import org.cytoscape.serviceapps.request.RequestBuilder;
import org.cytoscape.serviceapps.request.ServiceInput;
import org.cytoscape.serviceapps.testsupport.ContractNetwork;
import org.cytoscape.serviceapps.testsupport.FakeCx2Support;
import org.cytoscape.serviceapps.testsupport.TestNdex;
import org.cytoscape.serviceapps.testsupport.TestNetworks;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;

import static org.cytoscape.serviceapps.testsupport.Waits.until;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Runs against the fixture service: the whole submit, poll, fetch, apply, delete cycle. */
@ExtendWith(FixtureServerExtension.class)
class ServiceAppRunTest {

    private static final RunSettings FAST =
            new RunSettings(Duration.ofMillis(10), Duration.ofSeconds(20));

    private final ContractNetwork target = new ContractNetwork("target");
    private final CyNetworkViewManager viewManager = mock(CyNetworkViewManager.class);
    private final CyNetworkManager networkManager = TestNetworks.support().getNetworkManager();
    private final RequestBuilder requests =
            new RequestBuilder(
                    new FakeCx2Support(
                            networkManager,
                            viewManager,
                            ContractNetwork.views().getNetworkViewFactory()),
                    TestNdex.link());
    private final RenderServiceAppUI renderer =
            new RenderServiceAppUI(List.of(new LayoutsHandler(viewManager)));
    private final ActiveRuns active = new ActiveRuns();
    private final List<String> states = new CopyOnWriteArrayList<>();

    {
        when(viewManager.getNetworkViews(target.network)).thenReturn(List.of(target.view));
    }

    private ServiceAppRun run(FixtureServer fixture, String scenario, RunSettings settings) {
        return run(fixture, scenario, settings, transport());
    }

    private ServiceAppRun run(
            FixtureServer fixture, String scenario, RunSettings settings, HttpTransport transport) {
        return new ServiceAppRun(
                new ServiceAppRun.Plan(
                        scenario,
                        TestNetworks.scenario(scenario),
                        new ServiceInput(Map.of("Greeting", "hi"), Map.of()),
                        target.network,
                        target.view),
                new ServiceAppClient(fixture.url(scenario), transport),
                requests,
                renderer,
                () -> settings,
                active,
                (status, progress, message) ->
                        states.add(status + " " + (int) progress + " " + message));
    }

    private static HttpTransport transport() {
        return new JdkHttpTransport(Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    private double x(long node) {
        return target.view
                .getNodeView(target.node(node))
                .getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION);
    }

    private static List<String> methods(FixtureServer fixture) {
        List<String> methods = new ArrayList<>();
        fixture.requests().forEach(r -> methods.add(r.method()));
        return methods;
    }

    @Test
    void successAppliesTheResultThenDeletesTheTask(FixtureServer fixture) {
        RecordingTaskMonitor monitor = new RecordingTaskMonitor();

        run(fixture, "failure-slow", FAST).run(monitor);

        assertEquals(25.2, x(7));
        assertEquals("DELETE", methods(fixture).get(methods(fixture).size() - 1));
        assertTrue(fixture.liveTaskIds().isEmpty());
        assertEquals(1.0, monitor.progress());
        String last = states.get(states.size() - 1);
        assertTrue(last.startsWith("complete 100 Applied updateLayouts"), last);
        assertTrue(states.stream().anyMatch(s -> s.startsWith("processing 50")), states.toString());
        assertEquals(0, active.count("failure-slow"));
    }

    @Test
    void anEmptyNdexAddressIsExplainedInTheCompletionMessage(FixtureServer fixture) {
        RecordingTaskMonitor monitor = new RecordingTaskMonitor();

        run(fixture, "param-ndex-uuid", FAST).run(monitor);

        assertEquals(
                "complete 100 Applied updateLayouts. Notes: "
                        + NdexLink.EMPTY_NOTE
                        + "network \"target\" was never imported from or saved to NDEx",
                states.get(states.size() - 1));
    }

    @Test
    void completeIsReportedOnlyAfterTheResultIsApplied(FixtureServer fixture) {
        List<Double> xWhenComplete = new ArrayList<>();
        ServiceAppRun run =
                new ServiceAppRun(
                        new ServiceAppRun.Plan(
                                "hello",
                                TestNetworks.scenario("failure-slow"),
                                ServiceInput.empty(),
                                target.network,
                                target.view),
                        new ServiceAppClient(fixture.url("failure-slow"), transport()),
                        requests,
                        renderer,
                        () -> FAST,
                        active,
                        (status, progress, message) -> {
                            if (TaskStatus.COMPLETE.equals(status)) {
                                xWhenComplete.add(x(7));
                            }
                        });

        run.run(new RecordingTaskMonitor());

        assertEquals(List.of(25.2), xWhenComplete);
    }

    @Test
    void aFailedTaskAppliesNothingAndReportsTheServiceMessage(FixtureServer fixture) {
        double before = x(7);

        RunException failure =
                assertThrows(
                        RunException.class,
                        () -> run(fixture, "failure-failed", FAST).run(new RecordingTaskMonitor()));

        assertEquals("Service task failed: Algorithm failed: bad input", failure.getMessage());
        assertEquals(before, x(7));
        assertTrue(fixture.liveTaskIds().isEmpty(), "the failed task is deleted");
        assertTrue(states.get(states.size() - 1).startsWith("failed"), states.toString());
    }

    @Test
    void aTaskThatNeverReaches100TimesOut(FixtureServer fixture) {
        RunSettings shortTimeout = new RunSettings(Duration.ofMillis(10), Duration.ofMillis(300));
        double before = x(7);

        RunException failure =
                assertThrows(
                        RunException.class,
                        () ->
                                run(fixture, "failure-never-complete", shortTimeout)
                                        .run(new RecordingTaskMonitor()));

        assertEquals(
                "Service task failed: failure-never-complete took longer than 300 ms and was stopped.",
                failure.getMessage());
        assertEquals(before, x(7));
        assertTrue(methods(fixture).contains("DELETE"));
        assertTrue(fixture.liveTaskIds().isEmpty());
        assertTrue(states.get(states.size() - 1).startsWith("failed 50"), states.toString());
    }

    @Test
    void cancelDeletesTheTaskAndAppliesNothing(FixtureServer fixture) throws Exception {
        ServiceAppRun run = run(fixture, "failure-never-complete", FAST);
        double before = x(7);
        CompletableFuture<Void> running =
                CompletableFuture.runAsync(() -> run.run(new RecordingTaskMonitor()));
        until(
                () ->
                        !fixture.liveTaskIds().isEmpty()
                                && active.count("failure-never-complete") == 1);

        active.cancelApp("failure-never-complete");
        running.get(5, TimeUnit.SECONDS);

        assertTrue(methods(fixture).contains("DELETE"), "the service is told to stop");
        assertTrue(fixture.liveTaskIds().isEmpty());
        assertEquals(1, methods(fixture).stream().filter("DELETE"::equals).count());
        assertEquals(before, x(7));
        assertTrue(run.isCancelled());
        assertTrue(states.get(states.size() - 1).startsWith("failed"), states.toString());
        assertEquals(0, active.count("failure-never-complete"));
    }

    @Test
    void aFailedDeleteIsLoggedAndTheResultStays(FixtureServer fixture) {
        HttpTransport real = transport();
        HttpTransport deleteFails =
                (method, uri, body) -> {
                    if (method.equals("DELETE")) {
                        throw new IOException("connection reset");
                    }
                    return real.send(method, uri, body);
                };

        run(fixture, "failure-slow", FAST, deleteFails).run(new RecordingTaskMonitor());

        assertEquals(25.2, x(7));
        assertTrue(states.get(states.size() - 1).startsWith("complete"), states.toString());
        assertEquals(1, fixture.liveTaskIds().size(), "the remote task outlives the failed delete");
    }

    @Test
    void threeFailedStatusChecksInARowEndTheRun(FixtureServer fixture) {
        HttpTransport real = transport();
        AtomicInteger polls = new AtomicInteger();
        HttpTransport pollsFail =
                (method, uri, body) -> {
                    if (method.equals("GET") && uri.getPath().endsWith("/status")) {
                        polls.incrementAndGet();
                        throw new IOException("timed out");
                    }
                    return real.send(method, uri, body);
                };

        RunException failure =
                assertThrows(
                        RunException.class,
                        () ->
                                run(fixture, "failure-slow", FAST, pollsFail)
                                        .run(new RecordingTaskMonitor()));

        assertTrue(
                failure.getMessage()
                        .startsWith("Service task failed: lost contact with failure-slow"),
                failure.getMessage());
        assertEquals(RunSettings.MAX_FAILED_POLLS, polls.get());
        assertTrue(fixture.liveTaskIds().isEmpty());
    }

    @Test
    void aRefusedSubmitFailsWithTheReason(FixtureServer fixture) {
        RunException failure =
                assertThrows(
                        RunException.class,
                        () ->
                                run(fixture, "failure-submit-500", FAST)
                                        .run(new RecordingTaskMonitor()));

        assertTrue(
                failure.getMessage()
                        .startsWith(
                                "Service task failed: failure-submit-500 didn't accept the task"),
                failure.getMessage());
        assertEquals(List.of("POST"), methods(fixture));
    }

    @Test
    void aResultThatCantBeAppliedFailsTheTaskWithTheRenderingMessage(FixtureServer fixture) {
        // The menu path runs this task through Desktop's task manager, which shows the exception's
        // message in its error dialog; task status reports the same message.
        RunException failure =
                assertThrows(
                        RunException.class,
                        () ->
                                run(fixture, "result-malformed-layouts", FAST)
                                        .run(new RecordingTaskMonitor()));

        assertEquals(
                "Rendering failed at updateLayouts (1 of 1): updateLayouts data is not a list of node"
                        + " positions. Nothing was applied.",
                failure.getMessage());
        assertTrue(states.get(states.size() - 1).startsWith("failed"), states.toString());
        assertTrue(fixture.liveTaskIds().isEmpty(), "the task is still deleted");
    }

    /** A replacement reply for a GET of the task's {@code "status"} or {@code "result"}. */
    private interface Answer {
        /** The reply, or null to let the fixture answer. */
        HttpTransport.Response reply(String what) throws IOException;
    }

    /** The real fixture, with the task's status or result answered by {@code answer}. */
    private static HttpTransport replacing(String scenario, Answer answer) {
        HttpTransport real = transport();
        return (method, uri, body) -> {
            String path = uri.getPath();
            if (method.equals("GET") && path.startsWith("/" + scenario + "/")) {
                HttpTransport.Response reply =
                        answer.reply(path.endsWith("/status") ? "status" : "result");
                if (reply != null) {
                    return reply;
                }
            }
            return real.send(method, uri, body);
        };
    }

    private RunException fails(FixtureServer fixture, HttpTransport transport) {
        return assertThrows(
                RunException.class,
                () -> run(fixture, "hello", FAST, transport).run(new RecordingTaskMonitor()));
    }

    @Test
    void aResultThatCantBeFetchedFailsAsTheServicesFailureAndDeletesTheTask(FixtureServer fixture) {
        RunException failure =
                fails(
                        fixture,
                        replacing(
                                "hello",
                                what -> {
                                    if (what.equals("result")) {
                                        throw new IOException("connection reset");
                                    }
                                    return null;
                                }));

        assertTrue(
                failure.getMessage()
                        .startsWith("Service task failed: could not fetch the result: "),
                failure.getMessage());
        assertTrue(failure.getMessage().contains("connection reset"), failure.getMessage());
        assertTrue(states.get(states.size() - 1).startsWith("failed"), states.toString());
        assertTrue(fixture.liveTaskIds().isEmpty(), "the task is still deleted");
    }

    @Test
    void aFailureWithNoMessageSaysTheAppReportedAFailure(FixtureServer fixture) {
        HttpTransport.Response failed =
                new HttpTransport.Response(200, "{\"status\":\"failed\",\"progress\":100}");

        RunException failure = fails(fixture, replacing("hello", what -> failed));

        assertEquals("Service task failed: hello reported a failure", failure.getMessage());
    }

    @Test
    void aResultEntryThatIsNotAnActionFailsAsARenderingFailure(FixtureServer fixture) {
        HttpTransport.Response complete =
                new HttpTransport.Response(
                        200, "{\"status\":\"complete\",\"progress\":100,\"result\":[1]}");

        RunException failure =
                fails(fixture, replacing("hello", what -> what.equals("result") ? complete : null));

        assertEquals(
                "Rendering failed: task result entry 1 is not an action: 1", failure.getMessage());
        assertTrue(fixture.liveTaskIds().isEmpty(), "the task is still deleted");
    }

    @Test
    void concurrentRunsEachFinishAndCleanUp(FixtureServer fixture) throws Exception {
        List<CompletableFuture<Void>> runs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ServiceAppRun run = run(fixture, "failure-slow", FAST);
            runs.add(CompletableFuture.runAsync(() -> run.run(new RecordingTaskMonitor())));
        }
        CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);

        assertEquals(4, methods(fixture).stream().filter("POST"::equals).count());
        assertEquals(4, methods(fixture).stream().filter("DELETE"::equals).count());
        assertTrue(fixture.liveTaskIds().isEmpty());
        assertEquals(4, states.stream().filter(s -> s.startsWith("complete")).count());
    }

    @Test
    void settingsComeFromThePropertiesWithSafeFallbacks() {
        Properties properties = new Properties();
        properties.setProperty(RunSettings.POLL_INTERVAL_MS, "250");
        properties.setProperty(RunSettings.RUN_TIMEOUT_MS, "-1");

        RunSettings settings = RunSettings.from(properties);

        assertEquals(Duration.ofMillis(250), settings.pollInterval());
        assertEquals(RunSettings.DEFAULTS.timeout(), settings.timeout());
        assertEquals(RunSettings.DEFAULTS, RunSettings.from(null));
        assertNotEquals(RunSettings.DEFAULTS, settings);
    }
}
