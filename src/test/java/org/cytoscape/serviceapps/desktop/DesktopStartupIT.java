package org.cytoscape.serviceapps.desktop;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The app installs into Cytoscape 3.10 and starts without errors. */
@ExtendWith(DesktopExtension.class)
class DesktopStartupIT {

    @Test
    void theAppStartsCleanly(DesktopContainer desktop) {
        String logs = desktop.logs();

        assertTrue(
                logs.contains("Desktop Service App started"),
                "the app's start-up line is missing from Cytoscape's log");
        List<String> appErrors =
                logs.lines()
                        .filter(line -> line.contains("ERROR"))
                        .filter(line -> line.contains("org.cytoscape.serviceapps"))
                        .toList();
        assertEquals(List.of(), appErrors, "the app logged errors");
    }

    @Test
    void everyCommandIsPublishedOverCyRest(DesktopContainer desktop) throws IOException {
        String available = desktop.cyrest().text("/v1/commands/" + DesktopTestSupport.NAMESPACE);

        for (String command : List.of("list", "install", "uninstall", "status")) {
            assertTrue(
                    available.lines().anyMatch(line -> line.strip().equals(command)),
                    "serviceapps " + command + " is not published: " + available);
        }
    }
}
