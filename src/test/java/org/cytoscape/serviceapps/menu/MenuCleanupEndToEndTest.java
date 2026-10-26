package org.cytoscape.serviceapps.menu;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import org.cytoscape.application.swing.CyAction;
import org.cytoscape.serviceapps.client.JdkHttpTransport;
import org.cytoscape.serviceapps.client.ServiceAppClient;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.FixtureServerExtension;
import org.cytoscape.serviceapps.launch.ServiceAppLauncher;
import org.cytoscape.serviceapps.manager.ManageServiceAppsAction;
import org.cytoscape.serviceapps.model.CyWebMenuItem;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.serviceapps.run.ActiveRuns;
import org.cytoscape.serviceapps.testsupport.DesktopMenus;
import org.cytoscape.serviceapps.testsupport.FakeCx2Support;
import org.cytoscape.serviceapps.testsupport.ManualExecutor;
import org.cytoscape.serviceapps.testsupport.TestNetworks;
import org.cytoscape.view.model.CyNetworkViewManager;

import static org.cytoscape.serviceapps.testsupport.DesktopMenus.onEdtAndWait;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Uninstalling a service app leaves no empty submenu in Desktop's real menu bar, whether the
 * uninstall comes from the manager dialog (the EDT) or a command (another thread).
 */
@ExtendWith(FixtureServerExtension.class)
class MenuCleanupEndToEndTest {

    private static final List<String> INSTALLED = List.of("Apps > Fixture > Clustering > Hello");

    @TempDir Path dir;

    private final DesktopMenus desktop = new DesktopMenus();
    private ServiceAppRegistry registry;
    private ServiceAppLifecycle lifecycle;
    private FixtureServer fixture;

    @BeforeEach
    void setUp(FixtureServer fixture) {
        this.fixture = fixture;
        registry = ServiceAppRegistry.inDirectory(dir);
        lifecycle =
                new ServiceAppLifecycle(
                        desktop.registrar(),
                        registry,
                        mock(ServiceAppLauncher.class),
                        new FakeCx2Support(
                                TestNetworks.support().getNetworkManager(),
                                mock(CyNetworkViewManager.class),
                                null),
                        url ->
                                new ServiceAppClient(
                                        url,
                                        new JdkHttpTransport(
                                                Duration.ofSeconds(2), Duration.ofSeconds(5))),
                        new ActiveRuns(),
                        new ManualExecutor(),
                        new EmptySubmenus(desktop.swing()));
        lifecycle.start();
    }

    @AfterEach
    void tearDown() {
        lifecycle.shutdown();
    }

    private String install() {
        String name = TestNetworks.install(registry, fixture, "menu-multi-level");
        DesktopMenus.flush();
        return name;
    }

    @Test
    void anUninstallFromAnotherThreadLeavesNoEmptySubmenu() {
        String name = install();
        assertEquals(INSTALLED, desktop.tree("Apps"));

        registry.uninstall(name); // off the EDT: Desktop's removal is queued, then the purge
        DesktopMenus.flush();

        assertEquals(List.of(), desktop.tree("Apps"));
    }

    @Test
    void anUninstallOnTheEdtLeavesNoEmptySubmenu() {
        String name = install();

        onEdtAndWait(() -> registry.uninstall(name)); // as the manager dialog does

        assertEquals(List.of(), desktop.tree("Apps"));
    }

    @Test
    void anAppUnderServiceAppsLeavesInstallAndManageInPlace() {
        for (String title : List.of("Manage", "Install")) {
            CyAction action =
                    DesktopMenus.item(
                            ManageServiceAppsAction.MENU, title, title.equals("Manage") ? 1f : 2f);
            onEdtAndWait(() -> desktop.swing().addAction(action));
        }
        ServiceMetadata meta = TestNetworks.scenario("menu-root-analysis");
        meta.cyWebMenuItem.path = List.of(element("Community", 5.0), element("Hello", 20.0));
        registry.install(meta.name, meta.version, fixture.url("menu-root-analysis"), meta);
        DesktopMenus.flush();
        assertEquals(
                List.of(
                        "Apps > Service Apps > Manage",
                        "Apps > Service Apps > Install",
                        "Apps > Service Apps > Community > Hello"),
                desktop.tree("Apps"));

        registry.uninstall(meta.name);
        DesktopMenus.flush();

        assertEquals(
                List.of("Apps > Service Apps > Manage", "Apps > Service Apps > Install"),
                desktop.tree("Apps"));
    }

    private static CyWebMenuItem.PathElement element(String name, double gravity) {
        CyWebMenuItem.PathElement element = new CyWebMenuItem.PathElement();
        element.name = name;
        element.gravity = gravity;
        return element;
    }

    @Test
    void aReinstallShowsTheEntryAgain() {
        String name = install();
        registry.uninstall(name);
        DesktopMenus.flush();

        install();

        assertEquals(INSTALLED, desktop.tree("Apps"));
    }
}
