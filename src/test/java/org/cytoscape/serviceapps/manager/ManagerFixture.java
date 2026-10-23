package org.cytoscape.serviceapps.manager;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.serviceapps.client.JdkHttpTransport;
import org.cytoscape.serviceapps.client.ServiceAppClient;
import org.cytoscape.serviceapps.client.ServiceAppClientFactory;
import org.cytoscape.serviceapps.client.ServiceAppStatusChecker;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.form.FormSpecBuilder;
import org.cytoscape.serviceapps.launch.ServiceAppLauncher;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.serviceapps.render.RenderServiceAppUI;
import org.cytoscape.serviceapps.request.RequestBuilder;
import org.cytoscape.serviceapps.run.ActiveRuns;
import org.cytoscape.serviceapps.run.RunSettings;
import org.cytoscape.serviceapps.run.ServiceAppRunFactory;
import org.cytoscape.serviceapps.testsupport.FakeCx2Support;
import org.cytoscape.serviceapps.testsupport.ManualExecutor;
import org.cytoscape.serviceapps.testsupport.TestNetworks;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.TaskManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * The manager's collaborators for tests: a spied registry in a temp directory (real behaviour,
 * verifiable calls), a spied real launcher with a mocked task manager, and the fixture service.
 */
class ManagerFixture {

    static final String OFFLINE_URL = "http://127.0.0.1:9/offline";

    final FixtureServer fixture;
    final ServiceAppRegistry registry;
    final ServiceAppClientFactory clients =
            url ->
                    new ServiceAppClient(
                            url,
                            new JdkHttpTransport(Duration.ofSeconds(2), Duration.ofSeconds(5)));
    final FakeCx2Support cx2 =
            new FakeCx2Support(
                    TestNetworks.support().getNetworkManager(),
                    mock(CyNetworkViewManager.class),
                    null);
    final ActiveRuns active = new ActiveRuns();
    final ServiceAppRunFactory runs;
    final ServiceAppLauncher launcher;
    final ManualExecutor checks = new ManualExecutor();

    ManagerFixture(FixtureServer fixture, Path dir) {
        this.fixture = fixture;
        this.registry = spy(ServiceAppRegistry.inDirectory(dir));
        this.runs =
                new ServiceAppRunFactory(
                        registry,
                        clients,
                        new RequestBuilder(cx2),
                        new RenderServiceAppUI(List.of()),
                        () -> new RunSettings(Duration.ofMillis(10), Duration.ofSeconds(20)),
                        active);
        this.launcher =
                spy(
                        new ServiceAppLauncher(
                                registry,
                                new FormSpecBuilder(),
                                mock(TaskManager.class),
                                mock(CyApplicationManager.class),
                                runs,
                                cx2,
                                (title, message) -> {}));
    }

    ServiceAppManagerModel model() {
        return new ServiceAppManagerModel(
                registry, launcher, new ServiceAppStatusChecker(clients), checks);
    }

    String install(String scenario) {
        return TestNetworks.install(registry, fixture, scenario);
    }

    String installOffline(String name) {
        ServiceMetadata meta = TestNetworks.scenario("hello");
        meta.name = name;
        registry.install(name, meta.version, OFFLINE_URL, meta);
        return name;
    }
}
