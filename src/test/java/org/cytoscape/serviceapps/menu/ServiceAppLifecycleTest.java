package org.cytoscape.serviceapps.menu;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.serviceapps.client.JdkHttpTransport;
import org.cytoscape.serviceapps.client.ServiceAppClient;
import org.cytoscape.serviceapps.client.ServiceAppClientFactory;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.FixtureServerExtension;
import org.cytoscape.serviceapps.launch.ServiceAppLauncher;
import org.cytoscape.serviceapps.manager.ManageServiceAppsAction;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.placement.MenuPlacement;
import org.cytoscape.serviceapps.placement.MenuPlacementResolver;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.serviceapps.run.ActiveRuns;
import org.cytoscape.serviceapps.testsupport.FakeCx2Support;
import org.cytoscape.serviceapps.testsupport.ManualExecutor;
import org.cytoscape.serviceapps.testsupport.TestNetworks;
import org.cytoscape.serviceapps.testsupport.Waits;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.TaskFactory;

import static org.cytoscape.work.ServiceProperties.ENABLE_FOR;
import static org.cytoscape.work.ServiceProperties.MENU_GRAVITY;
import static org.cytoscape.work.ServiceProperties.PREFERRED_MENU;
import static org.cytoscape.work.ServiceProperties.TITLE;
import static org.cytoscape.work.ServiceProperties.TOOLTIP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/** Menu entries follow the installed apps: placement, enablement, refresh, uninstall, shutdown. */
@ExtendWith(FixtureServerExtension.class)
class ServiceAppLifecycleTest {

    @TempDir Path dir;

    private final CyServiceRegistrar registrar = mock(CyServiceRegistrar.class);
    private final Map<Object, Properties> live = new LinkedHashMap<>();
    private final ServiceAppClientFactory clients =
            url ->
                    new ServiceAppClient(
                            url,
                            new JdkHttpTransport(Duration.ofSeconds(2), Duration.ofSeconds(5)));
    private final FakeCx2Support cx2 =
            new FakeCx2Support(
                    TestNetworks.support().getNetworkManager(),
                    mock(CyNetworkViewManager.class),
                    null);
    private final ActiveRuns active = mock(ActiveRuns.class);
    private final ManualExecutor refresher = new ManualExecutor();
    private final EmptySubmenus submenus = mock(EmptySubmenus.class);
    private int registrations;

    private ServiceAppRegistry registry;
    private ServiceAppLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        doAnswer(
                        call -> {
                            registrations++;
                            live.put(call.getArgument(0), call.getArgument(2));
                            return null;
                        })
                .when(registrar)
                .registerService(any(), eq(TaskFactory.class), any(Properties.class));
        doAnswer(call -> live.remove(call.getArgument(0)))
                .when(registrar)
                .unregisterService(any(), eq(TaskFactory.class));
        registry = spy(ServiceAppRegistry.inDirectory(dir));
        lifecycle = newLifecycle();
    }

    private ServiceAppLifecycle newLifecycle() {
        return new ServiceAppLifecycle(
                registrar,
                registry,
                mock(ServiceAppLauncher.class),
                cx2,
                clients,
                active,
                refresher,
                submenus);
    }

    private String install(FixtureServer fixture, String scenario) {
        ServiceMetadata meta = TestNetworks.scenario(scenario);
        registry.install(meta.name, meta.version, fixture.url(scenario), meta);
        return meta.name;
    }

    private String install(String name, ServiceMetadata meta, String url) {
        meta.name = name;
        registry.install(name, meta.version, url, meta);
        return name;
    }

    private Properties entry(String name) {
        return live.entrySet().stream()
                .filter(e -> ((ServiceAppMenuTaskFactory) e.getKey()).appName().equals(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private ServiceAppMenuTaskFactory factory(String name) {
        return live.keySet().stream()
                .map(ServiceAppMenuTaskFactory.class::cast)
                .filter(f -> f.appName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void startupRegistersEntriesFromSavedMetadataBeforeAnyRefresh(FixtureServer fixture) {
        String name = install(fixture, "menu-root-apps");

        lifecycle.start();

        Properties props = entry(name);
        ServiceMetadata meta = TestNetworks.scenario("menu-root-apps");
        var placement = MenuPlacementResolver.resolve(meta).orElseThrow();
        assertEquals(placement.preferredMenu(), props.getProperty(PREFERRED_MENU));
        assertEquals(placement.title(), props.getProperty(TITLE));
        assertEquals(Double.toString(placement.gravity()), props.getProperty(MENU_GRAVITY));
        assertEquals("network", props.getProperty(ENABLE_FOR));
        assertEquals(name + " 1.0 from 127.0.0.1:" + fixture.port(), props.getProperty(TOOLTIP));
        assertTrue(factory(name).isReady());
        assertTrue(fixture.requests().isEmpty(), "nothing is fetched before the refresh runs");
        verify(registry, atLeastOnce()).list();
        verify(registry, atLeastOnce()).getMeta(name);
        assertEquals(1, refresher.pending());
    }

    @Test
    void appsNamingADesktopMenuGetAnEntryThereAndOthersUnderServiceApps(FixtureServer fixture) {
        Map<String, String> expectedTop =
                Map.of(
                        "menu-root-apps", "Apps",
                        "menu-root-tools-lowercase", "Tools",
                        "menu-root-edit", "Edit",
                        "menu-root-help", "Help",
                        "menu-root-layout", "Layout",
                        "menu-root-layouts", "Layout",
                        "menu-multi-level", "Apps",
                        "menu-empty-path", "Apps");
        List<String> underServiceApps =
                List.of(
                        "menu-root-analysis",
                        "menu-root-data",
                        "menu-root-unknown",
                        "menu-root-missing",
                        "menu-root-blank");
        expectedTop.keySet().forEach(s -> install(fixture, s));
        underServiceApps.forEach(s -> install(fixture, s));

        lifecycle.start();

        expectedTop.forEach(
                (scenario, top) -> {
                    Properties props = entry("Fixture " + scenario);
                    String menu = props.getProperty(PREFERRED_MENU);
                    assertTrue(
                            menu.equals(top) || menu.startsWith(top + "."), scenario + ": " + menu);
                    assertFalse(menu.startsWith(ManageServiceAppsAction.MENU), scenario);
                });
        for (String scenario : underServiceApps) {
            Properties props = entry("Fixture " + scenario);
            assertEquals(ManageServiceAppsAction.MENU, props.getProperty(PREFERRED_MENU), scenario);
            assertEquals("Hello", props.getProperty(TITLE), scenario);
        }
        assertEquals(expectedTop.size() + underServiceApps.size(), live.size());
        assertTrue(lifecycle.withoutEntry().isEmpty(), lifecycle.withoutEntry().toString());
    }

    @Test
    void anAppWhoseEntryWouldHaveNoTitleGetsNone(FixtureServer fixture) {
        ServiceMetadata untitled = TestNetworks.scenario("menu-root-analysis");
        untitled.name = " ";
        untitled.cyWebMenuItem.path = new ArrayList<>();
        registry.install("Untitled", untitled.version, fixture.url("menu-root-analysis"), untitled);

        lifecycle.start();

        assertNull(entry("Untitled"));
        assertEquals(List.of("Untitled"), lifecycle.withoutEntry());
    }

    @Test
    void enableForFollowsTheInputTypeAndScope() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("none", "always");
        expected.put("typeNone", "always");
        for (String type : List.of("node", "edge", "network")) {
            expected.put(type + "/dynamic", "network");
            expected.put(type + "/all", "network");
        }
        expected.put("node/selected", "selectedNodes");
        expected.put("edge/selected", "selectedEdges");
        expected.put("network/selected", "selectedNodesOrEdges");
        cx2.setAvailable(true);
        for (String key : expected.keySet()) {
            ServiceMetadata meta = TestNetworks.scenario("menu-root-apps");
            if (key.equals("none")) {
                meta.serviceInputDefinition = null;
            } else if (key.equals("typeNone")) {
                meta.serviceInputDefinition.type = "none";
            } else {
                String[] typeScope = key.split("/");
                meta.serviceInputDefinition.type = typeScope[0];
                meta.serviceInputDefinition.scope = typeScope[1];
                if (!typeScope[0].equals("network")) {
                    meta.serviceInputDefinition.inputNetwork = null;
                }
            }
            install(key, meta, "http://127.0.0.1:9/x");
        }

        lifecycle.start();

        expected.forEach(
                (key, enableFor) ->
                        assertEquals(enableFor, entry(key).getProperty(ENABLE_FOR), key));
    }

    @Test
    void anUnsupportedAppGetsAGreyedOutEntryWithTheReason(FixtureServer fixture) {
        String name = install(fixture, "unsupported-file");

        lifecycle.start();

        Properties props = entry(name);
        assertNull(props.getProperty(ENABLE_FOR), "no ENABLE_FOR, so Desktop asks isReady()");
        assertFalse(factory(name).isReady());
        assertTrue(
                props.getProperty(TOOLTIP)
                        .contains("Unavailable: it uses the parameter type 'file'"),
                props.getProperty(TOOLTIP));
    }

    @Test
    void networkAppsAreGreyedOutUntilCxSupportAppears(FixtureServer fixture) {
        cx2.setAvailable(false);
        String name = install(fixture, "menu-root-apps");
        lifecycle.start();
        assertFalse(factory(name).isReady());
        assertTrue(entry(name).getProperty(TOOLTIP).contains("CX Support"));

        cx2.setAvailable(true);

        assertTrue(factory(name).isReady());
        assertEquals("network", entry(name).getProperty(ENABLE_FOR));
    }

    @Test
    void offlineAtStartupTheEntryAppearsThenGreysOutWhenTheRefreshFails() {
        ServiceMetadata meta = TestNetworks.scenario("menu-root-apps");
        String name = install("offline", meta, "http://127.0.0.1:9/offline");
        lifecycle.start();
        assertTrue(factory(name).isReady());

        refresher.runAll();

        assertFalse(registry.get(name).orElseThrow().reachable());
        assertFalse(factory(name).isReady());
        assertTrue(entry(name).getProperty(TOOLTIP).endsWith("Unavailable: service unreachable"));
    }

    @Test
    void comingBackOnlineEnablesTheEntryAgain(FixtureServer fixture) {
        String name = install(fixture, "menu-root-apps");
        registry.setReachable(name, false);
        Instant installed = registry.get(name).orElseThrow().lastRefreshedAt();
        lifecycle.start();
        assertFalse(factory(name).isReady());
        Waits.sleep(5); // so the refresh gets a later timestamp

        refresher.runAll();

        assertTrue(registry.get(name).orElseThrow().reachable());
        assertTrue(factory(name).isReady());
        assertTrue(registry.get(name).orElseThrow().lastRefreshedAt().isAfter(installed));
    }

    @Test
    void anUpgradeUpdatesTheEntryAndForgetsOldDialogValues(FixtureServer fixture) {
        ServiceMetadata old = TestNetworks.scenario("hello");
        old.version = "0.9";
        registry.install(old.name, "0.9", fixture.url("hello"), old);
        JsonObject values = new JsonObject();
        values.add("parameters", new JsonObject());
        values.getAsJsonObject("parameters").addProperty("Greeting", "hey");
        registry.saveLastValues(old.name, values);
        lifecycle.start();
        assertTrue(entry(old.name).getProperty(TOOLTIP).contains(" 0.9 "));

        refresher.runAll();

        String version = TestNetworks.scenario("hello").version;
        assertEquals(version, registry.get(old.name).orElseThrow().version());
        assertTrue(entry(old.name).getProperty(TOOLTIP).contains(" " + version + " "));
        assertEquals(new JsonObject(), registry.getLastValues(old.name));
        assertEquals(1, live.size());
    }

    @Test
    void savingDialogValuesDoesNotReRegisterTheEntry(FixtureServer fixture) {
        String name = install(fixture, "menu-root-apps");
        lifecycle.start();
        int before = registrations;

        registry.saveLastValues(name, new JsonObject());
        registry.setReachable(name, true);

        assertEquals(before, registrations);
    }

    @Test
    void installingWhileRunningAddsTheEntry(FixtureServer fixture) {
        lifecycle.start();
        assertTrue(live.isEmpty());

        String name = install(fixture, "menu-multi-level");

        assertTrue(entry(name).getProperty(PREFERRED_MENU).startsWith("Apps."));
    }

    @Test
    void uninstallRemovesTheEntryAndCancelsTheAppsRuns(FixtureServer fixture) {
        String name = install(fixture, "menu-root-apps");
        lifecycle.start();

        registry.uninstall(name);

        assertTrue(live.isEmpty());
        verify(active).cancelApp(name);
        verify(submenus)
                .purge(
                        name,
                        MenuPlacementResolver.resolve(TestNetworks.scenario("menu-root-apps"))
                                .orElseThrow());
    }

    @Test
    void aMovedEntryHasItsOldSubmenusPurged(FixtureServer fixture) {
        String name = install(fixture, "menu-multi-level");
        lifecycle.start();
        MenuPlacement before =
                MenuPlacementResolver.resolve(registry.getMeta(name).orElseThrow()).orElseThrow();
        ServiceMetadata moved = registry.getMeta(name).orElseThrow();
        moved.cyWebMenuItem.root = "Tools";
        registry.updateMeta(name, moved.version, moved);

        verify(submenus).purge(name, before);
        assertEquals(1, live.size(), "registered again at the new place");
        verify(submenus, never()).purge(eq(name), argThat(p -> p.topMenu().equals("Tools")));
    }

    @Test
    void shutdownRemovesEveryEntryStopsRefreshingAndCancelsRuns(FixtureServer fixture) {
        install(fixture, "menu-root-apps");
        install(fixture, "menu-root-tools");
        lifecycle.start();
        assertEquals(2, live.size());

        lifecycle.shutdown();

        assertTrue(live.isEmpty());
        assertEquals(0, refresher.pending());
        verify(active).cancelAll();
        install(fixture, "menu-root-help");
        assertTrue(live.isEmpty(), "no longer listening");
    }

    @Test
    void aRestartRegistersTheSameEntriesFromSavedMetadata(FixtureServer fixture) {
        String name = install(fixture, "menu-multi-level");
        lifecycle.start();
        Properties first = entry(name);
        lifecycle.shutdown();

        registry = ServiceAppRegistry.inDirectory(dir);
        newLifecycle().start();

        assertEquals(first, entry(name));
    }
}
