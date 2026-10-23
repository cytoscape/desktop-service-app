package org.cytoscape.serviceapps.run;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The runs in progress per app, which uninstalling an app or stopping the app cancels. */
class ActiveRunsTest {

    private final ExecutorService threads = Executors.newFixedThreadPool(2);

    @AfterEach
    void stop() {
        threads.shutdownNow();
    }

    private static ServiceAppRun run(String app) {
        ServiceAppRun run = mock(ServiceAppRun.class);
        when(run.appName()).thenReturn(app);
        return run;
    }

    @Test
    void runsAreCountedPerAppAndCancelledByAppOrAll() {
        ActiveRuns active = new ActiveRuns();
        ServiceAppRun a1 = run("A");
        ServiceAppRun a2 = run("A");
        ServiceAppRun b = run("B");
        active.started(a1);
        active.started(a2);
        active.started(b);

        active.cancelApp("A");

        verify(a1).cancel();
        verify(a2).cancel();
        verify(b, never()).cancel();
        assertEquals(2, active.count("A"));

        active.finished(a1);
        active.finished(a2);
        assertEquals(0, active.count("A"));
        active.cancelAll();
        verify(b).cancel();
    }

    @Test
    void aRunStartedWhileTheAppsLastRunFinishesIsStillCountedAndCancelled() throws Exception {
        // Each round: one thread finishes the app's only run, emptying its set, while another
        // starts a new run. The new run must never land in a set that is no longer tracked.
        for (int round = 0; round < 5_000; round++) {
            ActiveRuns active = new ActiveRuns();
            ServiceAppRun finishing = run("A");
            ServiceAppRun starting = run("A");
            active.started(finishing);
            CountDownLatch go = new CountDownLatch(1);

            Future<?> finish =
                    threads.submit(
                            () -> {
                                go.await();
                                active.finished(finishing);
                                return null;
                            });
            Future<?> start =
                    threads.submit(
                            () -> {
                                go.await();
                                active.started(starting);
                                return null;
                            });
            go.countDown();
            finish.get(5, TimeUnit.SECONDS);
            start.get(5, TimeUnit.SECONDS);

            assertEquals(1, active.count("A"), "round " + round);
            active.cancelApp("A");
            verify(starting).cancel();
        }
    }
}
