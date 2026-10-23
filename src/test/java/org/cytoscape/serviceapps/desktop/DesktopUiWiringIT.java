package org.cytoscape.serviceapps.desktop;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.gson.JsonElement;

import org.cytoscape.serviceapps.app.AppSupport;
import org.cytoscape.serviceapps.contract.ContractHarness;
import org.cytoscape.serviceapps.contract.DockerTaskCallHarness;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.ScenarioCatalog;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.placement.MenuPlacementResolver;
import org.cytoscape.serviceapps.testsupport.TestNetworks;

import static org.cytoscape.serviceapps.testsupport.Waits.until;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The menu, dialog and manager wiring in real Cytoscape. CyREST can't list Desktop's menus, so this
 * reads the app's INFO log lines, whose exact text comes from the same code that builds the menus.
 */
@ExtendWith(DesktopExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DesktopUiWiringIT {

    /** Logged on every start, just before menu entries are registered. */
    private static final String STARTED_UI = "Service app form handler registered";

    private static final List<String> WITH_ENTRY =
            List.of(
                    "menu-root-apps",
                    "menu-multi-level",
                    "menu-root-tools-lowercase",
                    "menu-empty-path");
    private static final List<String> WITHOUT_ENTRY =
            List.of("menu-root-analysis", "menu-root-missing");

    private static final Duration WAIT = Duration.ofSeconds(60);

    @BeforeAll
    static void startClean(DesktopContainer desktop) throws IOException {
        DesktopTestSupport.uninstallAll(desktop.cyrest());
    }

    @AfterAll
    static void cleanUp(DesktopContainer desktop) throws IOException {
        DesktopTestSupport.uninstallAll(desktop.cyrest());
    }

    private static String app(String scenario) {
        return ScenarioCatalog.get(scenario).appName();
    }

    private static void install(DesktopContainer desktop, FixtureServer fixture, String scenario)
            throws IOException {
        CyRest.Reply reply =
                desktop.cyrest()
                        .post(
                                DesktopTestSupport.NAMESPACE,
                                "install",
                                Map.of(
                                        "name", app(scenario),
                                        "version", ScenarioCatalog.get(scenario).appVersion(),
                                        "url", DesktopExtension.containerUrl(fixture, scenario)));
        assertTrue(reply.ok(), reply.toString());
    }

    /** The log since Cytoscape last started the app's UI. */
    private static String sinceLastStart(DesktopContainer desktop) {
        String logs = desktop.logs();
        int start = logs.lastIndexOf(STARTED_UI);
        return start < 0 ? "" : logs.substring(start);
    }

    private static String awaitLog(DesktopContainer desktop, String line) {
        return awaitLog(desktop, line, logs -> logs.contains(line));
    }

    private static String awaitLog(DesktopContainer desktop, String what, Predicate<String> found) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            String logs = sinceLastStart(desktop);
            if (found.test(logs)) {
                return logs;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return fail("not logged within " + WAIT.toSeconds() + " s: " + what);
    }

    private static String registered(String scenario, String state) {
        ServiceMetadata meta = TestNetworks.scenario(scenario);
        return "Menu entry registered: "
                + MenuPlacementResolver.resolve(meta).orElseThrow().describe()
                + " for '"
                + meta.name
                + "' ("
                + state
                + ")";
    }

    @Test
    @Order(1)
    void startupRegistersTheManagerTheDialogHandlerAndFindsCxSupport(DesktopContainer desktop) {
        String logs = sinceLastStart(desktop);

        assertTrue(logs.startsWith(STARTED_UI), "the dialog field handler was registered");
        assertTrue(
                logs.contains(
                        "Service Apps menu registered: Tools > Service Apps > Manage, Install"),
                logs);
        assertTrue(
                desktop.logs().contains("CX Support CX2 writer found (cx2NetworkWriterFactory)"));
        assertTrue(
                desktop.logs()
                        .contains(
                                "CX Support CX2 reader found (cytoscapeCx2NetworkReaderFactory)"));
    }

    @Test
    @Order(2)
    void appsNamingADesktopMenuGetAnEntryAndOthersDoNot(
            DesktopContainer desktop, FixtureServer fixture) throws IOException {
        for (String scenario : WITH_ENTRY) {
            install(desktop, fixture, scenario);
        }
        for (String scenario : WITHOUT_ENTRY) {
            install(desktop, fixture, scenario);
        }

        for (String scenario : WITH_ENTRY) {
            awaitLog(desktop, registered(scenario, "enabled"));
        }
        for (String scenario : WITHOUT_ENTRY) {
            ServiceMetadata meta = TestNetworks.scenario(scenario);
            awaitLog(
                    desktop,
                    "No menu entry for '"
                            + meta.name
                            + "': "
                            + MenuPlacementResolver.whyNoPlacement(meta).orElseThrow());
        }
        assertEquals(
                WITH_ENTRY.size() + WITHOUT_ENTRY.size(),
                DesktopTestSupport.list(desktop.cyrest()).size());
    }

    @Test
    @Order(3)
    void anUnsupportedAppGetsADisabledEntryWithTheReason(
            DesktopContainer desktop, FixtureServer fixture) throws IOException {
        install(desktop, fixture, "unsupported-file");

        ServiceMetadata meta = TestNetworks.scenario("unsupported-file");
        awaitLog(
                desktop,
                registered(
                        "unsupported-file",
                        "disabled: " + AppSupport.unsupportedReason(meta, true).orElseThrow()));
    }

    @Test
    @Order(4)
    void uninstallRemovesTheEntry(DesktopContainer desktop) throws IOException {
        CyRest.Reply reply =
                desktop.cyrest()
                        .post(
                                DesktopTestSupport.NAMESPACE,
                                "uninstall",
                                Map.of("name", app("menu-root-apps")));
        assertTrue(reply.ok(), reply.toString());

        awaitLog(desktop, "Menu entry removed for '" + app("menu-root-apps") + "'");
    }

    @Test
    @Order(5)
    void afterARestartEntriesComeBackAndMetadataIsRefreshed(DesktopContainer desktop) {
        desktop.restart();

        String logs = sinceLastStart(desktop);
        for (String scenario : WITH_ENTRY) {
            if (!scenario.equals("menu-root-apps")) {
                assertTrue(logs.contains(registered(scenario, "enabled")), scenario + "\n" + logs);
            }
        }
        assertTrue(
                !logs.contains("for '" + app("menu-root-apps") + "'"),
                "the uninstalled app stays gone");
        for (String scenario : List.of("menu-multi-level", "menu-root-analysis")) {
            awaitLog(
                    desktop,
                    "Refreshed metadata for '" + app(scenario) + "' (reachable, version 1.0)");
        }
    }

    @Test
    @Order(6)
    void taskCallWorksAfterTheRestart(DesktopContainer desktop, FixtureServer fixture) {
        // A restart leaves no networks open: load the contract network, then run through the
        // command path, which was registered again at startup.
        DockerTaskCallHarness harness = new DockerTaskCallHarness(desktop, fixture, false);
        harness.reset();

        ContractHarness.Outcome outcome =
                harness.run("result-update-selection", ContractHarness.Ui.CURRENT_VIEW);

        assertTrue(outcome.succeeded(), outcome.message());
        assertEquals("Applied updateSelection", outcome.message());
        assertTrue(harness.nodeSelected(15));
    }

    @Test
    @Order(7)
    void uninstallingRemovesEmptiedSubmenusAndKeepsOnesStillInUse(DesktopContainer desktop)
            throws IOException {
        // Both lines are written after reading Desktop's live menu bar. Every other fixture app's
        // entry is at Apps > Fixture > Hello, so Fixture is still in use; Clustering isn't.
        uninstall(desktop, app("menu-multi-level"));
        awaitLog(
                desktop,
                "Submenus kept for '"
                        + app("menu-multi-level")
                        + "', still in use: Apps > Fixture");

        for (JsonElement app : DesktopTestSupport.list(desktop.cyrest())) {
            uninstall(desktop, app.getAsJsonObject().get("name").getAsString());
        }
        until(
                () ->
                        sinceLastStart(desktop)
                                .lines()
                                .anyMatch(
                                        line ->
                                                line.contains("Empty submenus removed for '")
                                                        && line.endsWith("': Apps > Fixture")));
    }

    private static void uninstall(DesktopContainer desktop, String name) throws IOException {
        CyRest.Reply reply =
                desktop.cyrest()
                        .post(DesktopTestSupport.NAMESPACE, "uninstall", Map.of("name", name));
        assertTrue(reply.ok(), reply.toString());
    }

    @Test
    @Order(8)
    void theAppLogsNoErrors(DesktopContainer desktop) {
        List<String> errors =
                desktop.logs()
                        .lines()
                        // Only the app's own loggers: Desktop logs its own errors, such as a
                        // CyREST readiness probe made before the app had loaded.
                        .filter(
                                line ->
                                        line.matches(
                                                ".*\\bERROR\\s+org\\.cytoscape\\.serviceapps\\..*"))
                        .toList();

        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }
}
