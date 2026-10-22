package org.cytoscape.serviceapps.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.model.ServiceMetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceAppRegistryTest {

    @TempDir Path dir;

    private static ServiceMetadata meta(String name, String version) {
        ServiceMetadata meta = new ServiceMetadata();
        meta.name = name;
        meta.version = version;
        meta.description = "about " + name;
        return meta;
    }

    @Test
    void startsEmptyWithoutAFile() {
        ServiceAppRegistry registry = ServiceAppRegistry.inDirectory(dir);

        assertTrue(registry.list().isEmpty());
        assertTrue(registry.get("x").isEmpty());
        assertTrue(registry.getMeta("x").isEmpty());
        assertEquals(new JsonObject(), registry.getLastValues("x"));
    }

    @Test
    void installedAppsSurviveARestart() {
        ServiceAppRegistry registry = ServiceAppRegistry.inDirectory(dir);
        registry.install("Beta", "2.0", "https://b.example/x", meta("Beta", "2.0"));
        registry.install("alpha", "1.0", "https://a.example/x", meta("alpha", "1.0"));
        JsonObject values = new JsonObject();
        values.addProperty("Greeting", "hi");
        registry.saveLastValues("alpha", values);

        ServiceAppRegistry reloaded = ServiceAppRegistry.inDirectory(dir);

        List<InstalledServiceApp> apps = reloaded.list();
        assertEquals(
                List.of("alpha", "Beta"), apps.stream().map(InstalledServiceApp::name).toList());
        InstalledServiceApp alpha = apps.get(0);
        assertEquals("1.0", alpha.version());
        assertEquals("https://a.example/x", alpha.url());
        assertTrue(alpha.reachable());
        assertEquals("about alpha", reloaded.getMeta("alpha").orElseThrow().description);
        assertEquals("hi", reloaded.getLastValues("alpha").get("Greeting").getAsString());
    }

    @Test
    void installingTheSameNameReplacesItAndKeepsRememberedValues() {
        ServiceAppRegistry registry = ServiceAppRegistry.inDirectory(dir);
        List<RegistryEvent> events = new ArrayList<>();
        registry.addListener(events::add);
        registry.install("Hello", "1.0", "https://h.example/v1", meta("Hello", "1.0"));
        JsonObject values = new JsonObject();
        values.addProperty("Greeting", "hi");
        registry.saveLastValues("Hello", values);

        registry.install("Hello", "2.0", "https://h.example/v2", meta("Hello", "2.0"));

        assertEquals(1, registry.list().size());
        InstalledServiceApp app = registry.get("Hello").orElseThrow();
        assertEquals("2.0", app.version());
        assertEquals("https://h.example/v2", app.url());
        assertEquals("hi", registry.getLastValues("Hello").get("Greeting").getAsString());
        assertEquals(
                List.of(
                        new RegistryEvent(RegistryEvent.Type.ADDED, "Hello"),
                        new RegistryEvent(RegistryEvent.Type.UPDATED, "Hello"),
                        new RegistryEvent(RegistryEvent.Type.UPDATED, "Hello")),
                events);
    }

    @Test
    void uninstallRemovesTheAppAndReportsUnknownNames() {
        ServiceAppRegistry registry = ServiceAppRegistry.inDirectory(dir);
        List<RegistryEvent> events = new ArrayList<>();
        registry.install("Hello", "1.0", "https://h.example", meta("Hello", "1.0"));
        registry.addListener(events::add);

        assertTrue(registry.uninstall("Hello"));
        assertFalse(registry.uninstall("Hello"));

        assertTrue(registry.list().isEmpty());
        assertTrue(ServiceAppRegistry.inDirectory(dir).list().isEmpty());
        assertEquals(List.of(new RegistryEvent(RegistryEvent.Type.REMOVED, "Hello")), events);
    }

    @Test
    void readsReturnCopies() {
        ServiceAppRegistry registry = ServiceAppRegistry.inDirectory(dir);
        ServiceMetadata original = meta("Hello", "1.0");
        registry.install("Hello", "1.0", "https://h.example", original);
        original.description = "changed by the caller";

        ServiceMetadata read = registry.getMeta("Hello").orElseThrow();
        read.description = "changed again";
        registry.getLastValues("Hello").addProperty("x", "y");

        assertEquals("about Hello", registry.getMeta("Hello").orElseThrow().description);
        assertFalse(registry.getLastValues("Hello").has("x"));
    }

    @Test
    void updateMetaFollowsTheLiveVersionAndMarksReachable() {
        ServiceAppRegistry registry = ServiceAppRegistry.inDirectory(dir);
        registry.install("Hello", "1.0", "https://h.example", meta("Hello", "1.0"));
        registry.setReachable("Hello", false);

        assertTrue(registry.updateMeta("Hello", "1.1", meta("Hello", "1.1")));
        assertFalse(registry.updateMeta("Nope", "1", meta("Nope", "1")));

        InstalledServiceApp app = registry.get("Hello").orElseThrow();
        assertEquals("1.1", app.version());
        assertTrue(app.reachable());
        assertEquals("1.1", registry.getMeta("Hello").orElseThrow().version);
    }

    @Test
    void setReachableOnlyAnnouncesChanges() {
        ServiceAppRegistry registry = ServiceAppRegistry.inDirectory(dir);
        registry.install("Hello", "1.0", "https://h.example", meta("Hello", "1.0"));
        List<RegistryEvent> events = new ArrayList<>();
        registry.addListener(events::add);

        registry.setReachable("Hello", true);
        registry.setReachable("Hello", false);

        assertFalse(registry.get("Hello").orElseThrow().reachable());
        assertEquals(List.of(new RegistryEvent(RegistryEvent.Type.UPDATED, "Hello")), events);
        assertFalse(registry.setReachable("Nope", false));
    }

    @Test
    void aCorruptFileIsMovedAsideAndTheRegistryStartsEmpty() throws IOException {
        Files.writeString(dir.resolve(RegistryStore.FILE_NAME), "{not json");

        ServiceAppRegistry registry = ServiceAppRegistry.inDirectory(dir);

        assertTrue(registry.list().isEmpty());
        try (Stream<Path> files = Files.list(dir)) {
            assertTrue(
                    files.anyMatch(
                            p ->
                                    p.getFileName()
                                            .toString()
                                            .startsWith("service-apps.json.corrupt-")));
        }
        registry.install("Hello", "1.0", "https://h.example", meta("Hello", "1.0"));
        assertEquals(1, ServiceAppRegistry.inDirectory(dir).list().size());
    }

    @Test
    void aFailingListenerDoesNotStopOthers() {
        ServiceAppRegistry registry = ServiceAppRegistry.inDirectory(dir);
        List<RegistryEvent> events = new ArrayList<>();
        registry.addListener(
                event -> {
                    throw new IllegalStateException("boom");
                });
        registry.addListener(events::add);

        registry.install("Hello", "1.0", "https://h.example", meta("Hello", "1.0"));

        assertEquals(1, events.size());
    }

    @Test
    void removedListenersHearNothing() {
        ServiceAppRegistry registry = ServiceAppRegistry.inDirectory(dir);
        List<RegistryEvent> events = new ArrayList<>();
        RegistryListener listener = events::add;
        registry.addListener(listener);
        registry.removeListener(listener);

        registry.install("Hello", "1.0", "https://h.example", meta("Hello", "1.0"));

        assertTrue(events.isEmpty());
    }

    @Test
    void concurrentInstallsAreAllSavedAndLeaveNoTempFiles() throws Exception {
        ServiceAppRegistry registry = ServiceAppRegistry.inDirectory(dir);
        int count = 40;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < count; i++) {
            String name = "app-" + i;
            pool.submit(
                    () -> {
                        start.await();
                        registry.install(
                                name, "1.0", "https://x.example/" + name, meta(name, "1.0"));
                        return null;
                    });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(count, registry.list().size());
        assertEquals(count, ServiceAppRegistry.inDirectory(dir).list().size());
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(
                    List.of(RegistryStore.FILE_NAME),
                    files.map(p -> p.getFileName().toString()).toList());
        }
    }
}
