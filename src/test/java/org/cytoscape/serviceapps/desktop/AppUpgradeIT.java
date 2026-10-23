package org.cytoscape.serviceapps.desktop;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.ScenarioCatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Upgrading or downgrading the Desktop Service App keeps the installed service apps: every version
 * reads the registry from the same folder, beside Desktop's per-version app data folders.
 */
@ExtendWith(DesktopExtension.class)
class AppUpgradeIT {

    private static final String APP_DATA = "/root/CytoscapeConfiguration/app-data/";
    private static final String REGISTRY = APP_DATA + "org.cytoscape.serviceapps/service-apps.json";
    private static final String UPGRADE_VERSION = "9.9.9";

    @TempDir Path dir;

    @Test
    void serviceAppsSurviveAnUpgradeAndADowngradeOfTheApp(
            DesktopContainer desktop, FixtureServer fixture) throws IOException {
        CyRest cyrest = desktop.cyrest();
        DesktopTestSupport.uninstallAll(cyrest);
        String original = runningVersion(cyrest);
        CyRest.Reply installed =
                cyrest.post(
                        DesktopTestSupport.NAMESPACE,
                        "install",
                        Map.of(
                                "url",
                                DesktopExtension.containerUrl(fixture, ScenarioCatalog.HELLO)));
        assertTrue(installed.ok(), installed.toString());
        assertTrue(
                desktop.exec("cat", REGISTRY).contains("\"Hello World\""),
                "kept in the folder without a version");
        assertEquals("", versionedRegistries(desktop), "nothing in Desktop's per-version folders");

        try {
            desktop.replaceApp(AppJars.withBundleVersion(desktop.appJar(), UPGRADE_VERSION, dir));

            assertEquals(UPGRADE_VERSION, runningVersion(cyrest), "Desktop runs the upgrade");
            assertServiceAppIsBack(desktop, cyrest);
        } finally {
            desktop.replaceApp(desktop.appJar());
        }

        assertEquals(original, runningVersion(cyrest), "Desktop runs the original again");
        assertServiceAppIsBack(desktop, cyrest);
        assertEquals("", versionedRegistries(desktop));
        DesktopTestSupport.uninstallAll(cyrest);
    }

    private static void assertServiceAppIsBack(DesktopContainer desktop, CyRest cyrest)
            throws IOException {
        JsonArray apps = DesktopTestSupport.list(cyrest);
        assertEquals(1, apps.size(), apps.toString());
        assertEquals("Hello World", apps.get(0).getAsJsonObject().get("name").getAsString());
        String logs = desktop.logs();
        String sinceStart = logs.substring(logs.lastIndexOf("Service app form handler registered"));
        assertTrue(
                sinceStart.contains("Desktop Service App started with 1 service app(s) installed"),
                sinceStart);
        assertTrue(
                sinceStart.contains(
                        "Menu entry registered: Apps > Fixture > Hello for 'Hello World'"),
                sinceStart);
    }

    /** Registry files in Desktop's per-version folders for this app, one per line. */
    private static String versionedRegistries(DesktopContainer desktop) {
        return desktop.exec(
                        "sh",
                        "-c",
                        "ls "
                                + APP_DATA
                                + "org.cytoscape.serviceapps-*/service-apps.json"
                                + " 2>/dev/null || true")
                .strip();
    }

    /** The Desktop Service App's version, as Desktop's App Manager reports it. */
    private static String runningVersion(CyRest cyrest) throws IOException {
        CyRest.Reply reply = cyrest.post("apps", "list installed", Map.of());
        assertTrue(reply.ok(), reply.toString());
        List<String> versions = new ArrayList<>();
        for (JsonElement app : reply.data().getAsJsonArray()) {
            if ("Desktop Service App".equals(app.getAsJsonObject().get("appName").getAsString())) {
                versions.add(app.getAsJsonObject().get("version").getAsString());
            }
        }
        assertEquals(1, versions.size(), reply.toString());
        return versions.get(0);
    }
}
