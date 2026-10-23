package org.cytoscape.serviceapps.registry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.model.ServiceMetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry is kept in one folder for every version of the Desktop Service App, beside Desktop's
 * per-version app data folders, so upgrading the app keeps every installed service app.
 */
class RegistryLocationTest {

    private static final String BUNDLE = "org.cytoscape.serviceapps";

    @TempDir Path config;

    /** Desktop's folder for one version of the app, as CyApplicationConfiguration names it. */
    private File desktopFolder(String version) {
        return config.resolve("app-data").resolve(BUNDLE + "-" + version).toFile();
    }

    private ServiceAppRegistry registryOf(String version) {
        return ServiceAppRegistry.inDirectory(
                ServiceAppRegistry.directoryFor(desktopFolder(version), BUNDLE));
    }

    private static ServiceMetadata meta(String name, String version) {
        ServiceMetadata meta = new ServiceMetadata();
        meta.name = name;
        meta.version = version;
        meta.description = "about " + name;
        return meta;
    }

    @ParameterizedTest(name = "version \"{0}\"")
    @ValueSource(strings = {"0.1.0.SNAPSHOT", "0.1.0", "0.2.0", "1.0.0.beta-2", ""})
    void everyVersionOfTheAppUsesTheSameFolderBesideDesktops(String version) {
        assertEquals(
                config.resolve("app-data").resolve(BUNDLE),
                ServiceAppRegistry.directoryFor(desktopFolder(version), BUNDLE));
    }

    @Test
    void anUpgradeKeepsEveryInstalledAppWithItsStateAndADowngradeSeesLaterChanges() {
        ServiceAppRegistry before = registryOf("0.1.0");
        before.install("Hello", "1.0", "https://h.example/hello", meta("Hello", "1.0"));
        before.install("Other", "2.0", "https://o.example/other", meta("Other", "2.0"));
        JsonObject values = new JsonObject();
        values.addProperty("Greeting", "hi");
        before.saveLastValues("Hello", values);
        before.setReachable("Other", false);

        ServiceAppRegistry upgraded = registryOf("0.2.0");

        assertEquals(before.list(), upgraded.list(), "names, versions, URLs, times, reachability");
        assertEquals("about Hello", upgraded.getMeta("Hello").orElseThrow().description);
        assertEquals(values, upgraded.getLastValues("Hello"));
        assertFalse(upgraded.get("Other").orElseThrow().reachable());

        upgraded.uninstall("Other");
        assertEquals(
                List.of("Hello"),
                registryOf("0.1.0").list().stream().map(InstalledServiceApp::name).toList(),
                "the older version reads the same file");
    }

    @Test
    void nothingIsWrittenToDesktopsVersionedFolder() throws IOException {
        ServiceAppRegistry registry = registryOf("0.1.0");

        registry.install("Hello", "1.0", "https://h.example/hello", meta("Hello", "1.0"));
        registry.saveLastValues("Hello", new JsonObject());

        assertTrue(
                Files.isRegularFile(config.resolve("app-data/" + BUNDLE + "/service-apps.json")));
        assertFalse(Files.exists(desktopFolder("0.1.0").toPath()));
    }

    @Test
    void aRegistryLeftInAVersionedFolderIsNeitherReadNorChanged() throws IOException {
        // Where builds before this change kept it.
        ServiceAppRegistry old = ServiceAppRegistry.inDirectory(desktopFolder("0.1.0").toPath());
        old.install("Old", "1.0", "https://old.example", meta("Old", "1.0"));
        Path oldFile = desktopFolder("0.1.0").toPath().resolve("service-apps.json");
        String oldContent = Files.readString(oldFile);

        ServiceAppRegistry registry = registryOf("0.1.0");
        registry.install("Hello", "1.0", "https://h.example/hello", meta("Hello", "1.0"));

        assertEquals(
                List.of("Hello"), registry.list().stream().map(InstalledServiceApp::name).toList());
        assertEquals(oldContent, Files.readString(oldFile));
    }

    @Test
    void aFirstRunWithNoAppDataFolderStartsEmptyAndCreatesItOnTheFirstSave() {
        assertFalse(Files.exists(config.resolve("app-data")));
        ServiceAppRegistry registry = registryOf("0.1.0");
        assertTrue(registry.list().isEmpty());
        assertFalse(Files.exists(config.resolve("app-data")), "reading creates nothing");

        registry.install("Hello", "1.0", "https://h.example/hello", meta("Hello", "1.0"));

        assertEquals(1, registryOf("0.2.0").list().size());
    }

    @Test
    void aMissingFolderOrBundleNameIsAClearError() {
        assertEquals(
                "Cytoscape gave no app data folder for " + BUNDLE + ": null",
                assertThrows(
                                IllegalStateException.class,
                                () -> ServiceAppRegistry.directoryFor(null, BUNDLE))
                        .getMessage());
        assertThrows(
                IllegalStateException.class,
                () -> ServiceAppRegistry.directoryFor(new File("no-parent"), BUNDLE));
        for (String name : new String[] {null, " "}) {
            assertEquals(
                    "the app's bundle has no symbolic name",
                    assertThrows(
                                    IllegalStateException.class,
                                    () -> ServiceAppRegistry.directoryFor(desktopFolder("1"), name))
                            .getMessage());
        }
    }
}
