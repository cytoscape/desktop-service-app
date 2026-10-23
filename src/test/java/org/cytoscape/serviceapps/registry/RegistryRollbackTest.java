package org.cytoscape.serviceapps.registry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.model.ServiceMetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A change whose save fails, such as on a full disk, is undone in memory and announces nothing:
 * what the registry reports always matches what is on disk.
 */
class RegistryRollbackTest {

    @TempDir Path dir;

    private boolean failSaves;
    private ServiceAppRegistry registry;
    private final List<RegistryEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RegistryStore store =
                new RegistryStore(dir.resolve(RegistryStore.FILE_NAME)) {
                    @Override
                    void save(Map<String, Entry> apps) throws IOException {
                        if (failSaves) {
                            throw new IOException("No space left on device");
                        }
                        super.save(apps);
                    }
                };
        registry = new ServiceAppRegistry(store);
        registry.install("Hello", "1.0", "https://h.example/hello", meta("Hello", "1.0"));
        JsonObject values = new JsonObject();
        values.addProperty("Greeting", "hi");
        registry.saveLastValues("Hello", values);
        registry.addListener(events::add);
        failSaves = true;
    }

    private static ServiceMetadata meta(String name, String version) {
        ServiceMetadata meta = new ServiceMetadata();
        meta.name = name;
        meta.version = version;
        return meta;
    }

    private void assertUnchanged() {
        InstalledServiceApp app = registry.get("Hello").orElseThrow();
        assertEquals("1.0", app.version());
        assertEquals("https://h.example/hello", app.url());
        assertTrue(app.reachable());
        assertEquals("1.0", registry.getMeta("Hello").orElseThrow().version);
        assertEquals("hi", registry.getLastValues("Hello").get("Greeting").getAsString());
        assertEquals(1, registry.list().size());
        assertTrue(events.isEmpty(), "nothing is announced: " + events);
        // and what's on disk agrees
        assertEquals(registry.list(), ServiceAppRegistry.inDirectory(dir).list());
    }

    @Test
    void aFailedInstallOfANewAppLeavesItUninstalled() {
        assertThrows(
                UncheckedIOException.class,
                () ->
                        registry.install(
                                "Other", "1.0", "https://h.example/other", meta("Other", "1.0")));

        assertFalse(registry.get("Other").isPresent());
        assertUnchanged();
    }

    @Test
    void aFailedUpgradeKeepsTheInstalledVersion() {
        assertThrows(
                UncheckedIOException.class,
                () ->
                        registry.install(
                                "Hello", "2.0", "https://h.example/v2", meta("Hello", "2.0")));

        assertUnchanged();
    }

    @Test
    void aFailedUninstallKeepsTheApp() {
        assertThrows(UncheckedIOException.class, () -> registry.uninstall("Hello"));

        assertUnchanged();
    }

    @Test
    void aFailedMetadataUpdateKeepsTheOldMetadata() {
        assertThrows(
                UncheckedIOException.class,
                () -> registry.updateMeta("Hello", "2.0", meta("Hello", "2.0")));

        assertUnchanged();
    }

    @Test
    void aFailedReachabilityChangeKeepsTheOldValue() {
        assertThrows(UncheckedIOException.class, () -> registry.setReachable("Hello", false));

        assertUnchanged();
    }

    @Test
    void aFailedMetadataUpdateOfAnUnreachableAppKeepsItUnreachable() throws IOException {
        failSaves = false;
        registry.setReachable("Hello", false);
        events.clear();
        failSaves = true;

        assertThrows(
                UncheckedIOException.class,
                () -> registry.updateMeta("Hello", "1.0", meta("Hello", "1.0")));

        assertFalse(registry.get("Hello").orElseThrow().reachable(), "updateMeta's true undone");
        assertFalse(ServiceAppRegistry.inDirectory(dir).get("Hello").orElseThrow().reachable());
        assertTrue(events.isEmpty(), events.toString());
    }

    @Test
    void aSaveThatFailsOnDiskLeavesNoTemporaryFile(@TempDir Path other) throws IOException {
        // A non-empty directory where the file should go: writing works, moving into place fails.
        Path file = other.resolve(RegistryStore.FILE_NAME);
        ServiceAppRegistry blocked = new ServiceAppRegistry(new RegistryStore(file));
        Files.createDirectories(file.resolve("blocker"));

        assertThrows(
                UncheckedIOException.class,
                () -> blocked.install("Hello", "1.0", "https://h.example", meta("Hello", "1.0")));

        assertTrue(blocked.list().isEmpty());
        try (Stream<Path> files = Files.list(other)) {
            assertEquals(List.of(file), files.toList(), "only the blocker is left");
        }
    }

    @Test
    void aFailedSaveOfDialogValuesKeepsTheOldValues() {
        assertThrows(
                UncheckedIOException.class,
                () -> registry.saveLastValues("Hello", new JsonObject()));

        assertUnchanged();
    }

    @Test
    void theRegistryWorksAgainOnceSavesSucceed() {
        assertThrows(UncheckedIOException.class, () -> registry.uninstall("Hello"));
        failSaves = false;

        assertTrue(registry.uninstall("Hello"));

        assertTrue(ServiceAppRegistry.inDirectory(dir).list().isEmpty());
    }
}
