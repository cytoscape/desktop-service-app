package org.cytoscape.serviceapps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts the packaging contract of the built bundle. Cytoscape silently refuses an app whose OSGi
 * headers are wrong, so these checks fail the build instead.
 */
class BundleManifestIT {

    private static Path bundleJar() throws IOException {
        Path libs = Path.of("build", "libs");
        assertTrue(Files.isDirectory(libs), "run `make build` first: " + libs.toAbsolutePath());
        try (Stream<Path> jars = Files.list(libs)) {
            return jars.filter(p -> p.getFileName().toString().startsWith("desktop-service-app"))
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no bundle jar in " + libs));
        }
    }

    @Test
    void declaresTheBundleIdentityAndActivator() throws IOException {
        try (JarFile jar = new JarFile(bundleJar().toFile())) {
            Attributes main = jar.getManifest().getMainAttributes();

            assertEquals("org.cytoscape.serviceapps", main.getValue("Bundle-SymbolicName"));
            assertEquals("Desktop Service App", main.getValue("Bundle-Name"));
            assertEquals(
                    "org.cytoscape.serviceapps.CyActivator", main.getValue("Bundle-Activator"));
            assertNotNull(main.getValue("Bundle-Version"));
            assertTrue(
                    main.getValue("Require-Capability").contains("(version=17)"),
                    main.getValue("Require-Capability"));
        }
    }

    @Test
    void embedsGsonAndDoesNotImportIt() throws IOException {
        try (JarFile jar = new JarFile(bundleJar().toFile())) {
            assertNotNull(jar.getEntry("com/google/gson/Gson.class"));
            String imports = jar.getManifest().getMainAttributes().getValue("Import-Package");
            assertFalse(imports.contains("com.google.gson"), imports);
            assertTrue(imports.contains("org.cytoscape.work"), imports);
        }
    }

    @Test
    void leavesOutWhatTheRuntimeProvidesOrCannotLoad() throws IOException {
        try (JarFile jar = new JarFile(bundleJar().toFile())) {
            assertTrue(
                    jar.stream().noneMatch(e -> e.getName().startsWith("org/slf4j/")),
                    "SLF4J must not be embedded");
            assertTrue(
                    jar.stream().noneMatch(e -> e.getName().startsWith("META-INF/versions/")),
                    "multi-release classes break Felix in Cytoscape 3.10");
            assertTrue(
                    jar.stream()
                            .noneMatch(
                                    e ->
                                            e.getName()
                                                    .startsWith(
                                                            "org/cytoscape/serviceapps/fixture/")),
                    "the test fixture must not ship");
        }
    }

    @Test
    void shipsTheDefaultSettings() throws IOException {
        try (JarFile jar = new JarFile(bundleJar().toFile())) {
            assertNotNull(jar.getEntry("serviceapps.props"));
        }
    }
}
