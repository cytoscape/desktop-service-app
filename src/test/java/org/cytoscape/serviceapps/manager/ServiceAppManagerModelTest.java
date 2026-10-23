package org.cytoscape.serviceapps.manager;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.serviceapps.client.ServiceAppStatusChecker;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.FixtureServerExtension;
import org.cytoscape.serviceapps.manager.ServiceAppManagerModel.Row;
import org.cytoscape.serviceapps.manager.ServiceAppManagerModel.Status;
import org.cytoscape.serviceapps.menu.EmptySubmenus;
import org.cytoscape.serviceapps.menu.ServiceAppLifecycle;
import org.cytoscape.serviceapps.request.ServiceInput;
import org.cytoscape.serviceapps.run.RecordingTaskMonitor;
import org.cytoscape.serviceapps.run.RunListener;
import org.cytoscape.serviceapps.run.ServiceAppRun;
import org.cytoscape.serviceapps.testsupport.ManualExecutor;
import org.cytoscape.serviceapps.testsupport.TestNetworks;

import static org.cytoscape.serviceapps.testsupport.Waits.until;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The manager without Swing: rows, live status, Invoke, Uninstall, live updates, close. */
@ExtendWith(FixtureServerExtension.class)
class ServiceAppManagerModelTest {

    @TempDir Path dir;

    private ManagerFixture f;
    private ServiceAppManagerModel model;
    private final AtomicInteger changes = new AtomicInteger();

    @BeforeEach
    void setUp(FixtureServer fixture) {
        f = new ManagerFixture(fixture, dir);
        model = f.model();
        model.addChangeListener(changes::incrementAndGet);
    }

    private Row row(String name) {
        return model.rows().stream().filter(r -> r.name().equals(name)).findFirst().orElse(null);
    }

    @Test
    void noAppsMeansNoRows() {
        model.open();

        assertTrue(model.rows().isEmpty());
        verify(f.registry, times(2)).list();
    }

    @Test
    void rowsAreSortedByNameAndShowCheckingThenTheLiveStatus() {
        String noMenu = f.install("menu-root-missing");
        String online = f.install("hello");
        String offline = f.installOffline("Alpha offline");

        model.open();

        assertEquals(
                List.of(offline, noMenu, online).stream()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList(),
                model.rows().stream().map(Row::name).toList());
        model.rows().forEach(r -> assertEquals(Status.CHECKING, r.status(), r.name()));
        assertEquals(3, f.checks.pending());

        f.checks.runAll();

        assertEquals(Status.ONLINE, row(online).status());
        assertEquals(Status.ONLINE, row(noMenu).status());
        assertEquals(Status.OFFLINE, row(offline).status());
        assertEquals("Online", row(online).status().text());
        assertEquals("Offline", row(offline).status().text());
        verify(f.registry).setReachable(offline, false);
    }

    @Test
    void anUnreachableAppCantBeInvokedOnceTheCheckFindsIt() {
        String name = f.installOffline("Gone away");
        model.open();
        assertTrue(row(name).invokable(), "reachable when installed");

        f.checks.runAll();

        assertFalse(row(name).invokable());
        assertEquals("service unreachable", row(name).invokeDisabledReason());
    }

    @Test
    void aCheckThatThrowsShowsTheAppOfflineInsteadOfCheckingForever() {
        String name = f.install("hello");
        ServiceAppStatusChecker broken = mock(ServiceAppStatusChecker.class);
        when(broken.check(anyString())).thenThrow(new IllegalArgumentException("bad URL"));
        ServiceAppManagerModel model =
                new ServiceAppManagerModel(f.registry, f.launcher, broken, f.checks);
        model.open();

        f.checks.runAll();

        Row row = model.rows().get(0);
        assertEquals(Status.OFFLINE, row.status());
        assertEquals("The status check failed: bad URL", row.statusDetail());
        verify(f.registry).setReachable(name, false);
    }

    @Test
    void anUnsupportedAppCantBeInvokedAndSaysWhy() {
        String name = f.install("unsupported-file");
        model.open();

        assertTrue(
                row(name).invokeDisabledReason().contains("'file'"),
                row(name).invokeDisabledReason());
    }

    @Test
    void aSupportedAppCanBeInvoked() {
        String name = f.install("hello");
        model.open();
        assertNull(row(name).invokeDisabledReason());

        model.invoke(name);

        verify(f.launcher, times(1)).launch(name);
    }

    @Test
    void uninstallUsesTheRegistryAndRemovesTheRow() {
        String keep = f.install("hello");
        String gone = f.install("menu-root-apps");
        model.open();

        model.uninstall(gone);

        verify(f.registry).uninstall(gone);
        assertEquals(List.of(keep), model.rows().stream().map(Row::name).toList());
    }

    @Test
    void installsAndUninstallsElsewhereShowUpWhileOpen() {
        model.open();
        int before = changes.get();

        String name = f.install("hello");

        assertEquals(Status.CHECKING, row(name).status());
        assertTrue(changes.get() > before);
        f.checks.runAll();
        assertEquals(Status.ONLINE, row(name).status());

        f.registry.uninstall(name);
        assertTrue(model.rows().isEmpty());
    }

    @Test
    void closingStopsPendingChecksAndRegistryUpdates() {
        f.install("hello");
        model.open();
        assertEquals(1, model.pendingChecks());

        model.close();

        assertEquals(0, model.pendingChecks());
        int before = changes.get();
        f.checks.runAll();
        f.install("menu-root-apps");
        assertEquals(before, changes.get());
        assertFalse(model.isOpen());
    }

    @Test
    void reopeningChecksTheStatusAgain() {
        String name = f.install("hello");
        model.open();
        f.checks.runAll();
        assertEquals(Status.ONLINE, row(name).status());
        model.close();

        model.open();

        assertEquals(Status.CHECKING, row(name).status());
        assertEquals(1, f.checks.pending());
    }

    @Test
    void uninstallingAnAppWithARunInProgressCancelsItAndDeletesTheTask() throws Exception {
        String name = f.install("failure-never-complete");
        ServiceAppLifecycle lifecycle =
                new ServiceAppLifecycle(
                        mock(CyServiceRegistrar.class),
                        f.registry,
                        f.launcher,
                        f.cx2,
                        f.clients,
                        f.active,
                        new ManualExecutor(),
                        EmptySubmenus.NONE);
        lifecycle.start();
        ServiceAppRun run =
                f.runs.create(
                        name,
                        new ServiceInput(Map.of("Greeting", "hi"), Map.of()),
                        TestNetworks.withEveryColumnType(),
                        null,
                        RunListener.NONE);
        CompletableFuture<Void> running =
                CompletableFuture.runAsync(() -> run.run(new RecordingTaskMonitor()));
        until(() -> f.active.count(name) == 1 && !f.fixture.liveTaskIds().isEmpty());
        model.open();

        model.uninstall(name);

        running.get(5, TimeUnit.SECONDS);
        assertTrue(run.isCancelled());
        assertTrue(f.fixture.requests().stream().anyMatch(r -> r.method().equals("DELETE")));
        assertTrue(f.fixture.liveTaskIds().isEmpty());
    }
}
