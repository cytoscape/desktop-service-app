package org.cytoscape.serviceapps.desktop;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.ScenarioCatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Installed service apps survive a Cytoscape restart. */
@ExtendWith(DesktopExtension.class)
class RestartPersistenceIT {

    @Test
    void installedAppsAreStillListedAfterARestart(DesktopContainer desktop, FixtureServer fixture)
            throws IOException {
        CyRest cyrest = desktop.cyrest();
        DesktopTestSupport.uninstallAll(cyrest);
        CyRest.Reply installed =
                cyrest.post(
                        DesktopTestSupport.NAMESPACE,
                        "install",
                        Map.of(
                                "name",
                                "Hello World",
                                "version",
                                "1.0",
                                "url",
                                DesktopExtension.containerUrl(fixture, ScenarioCatalog.HELLO)));
        assertTrue(installed.ok(), installed.toString());

        desktop.restart();

        JsonArray apps = DesktopTestSupport.list(cyrest);
        assertEquals(1, apps.size());
        JsonObject app = apps.get(0).getAsJsonObject();
        assertEquals("Hello World", app.get("name").getAsString());
        assertEquals("1.0", app.get("version").getAsString());
        assertTrue(desktop.logs().contains("Desktop Service App started with 1 service app(s)"));

        DesktopTestSupport.uninstallAll(cyrest);
    }
}
