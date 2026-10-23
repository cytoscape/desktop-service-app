package org.cytoscape.serviceapps.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/** Copies of the app jar, for tests that change the app's version. */
final class AppJars {

    private AppJars() {}

    /**
     * A copy of {@code jar} whose {@code Bundle-Version} is {@code version}, which Desktop reads as
     * the app's version, named {@code desktop-service-app-<version>.jar} in {@code directory}.
     */
    static Path withBundleVersion(Path jar, String version, Path directory) throws IOException {
        Path copy = directory.resolve("desktop-service-app-" + version + ".jar");
        try (JarInputStream in = new JarInputStream(Files.newInputStream(jar))) {
            Manifest manifest = new Manifest(in.getManifest());
            manifest.getMainAttributes().put(new Attributes.Name("Bundle-Version"), version);
            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(copy), manifest)) {
                copyEntries(in, out);
            }
        }
        return copy;
    }

    private static void copyEntries(JarInputStream in, JarOutputStream out) throws IOException {
        for (JarEntry entry = in.getNextJarEntry(); entry != null; entry = in.getNextJarEntry()) {
            out.putNextEntry(new JarEntry(entry.getName()));
            transfer(in, out);
            out.closeEntry();
        }
    }

    private static void transfer(InputStream in, OutputStream out) throws IOException {
        in.transferTo(out);
    }
}
