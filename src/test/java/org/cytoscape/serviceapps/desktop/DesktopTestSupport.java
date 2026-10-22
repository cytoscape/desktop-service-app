package org.cytoscape.serviceapps.desktop;

import java.io.IOException;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shared steps for the Desktop integration tests. */
final class DesktopTestSupport {

    static final String NAMESPACE = "serviceapps";

    private DesktopTestSupport() {}

    /** The installed apps, read over CyREST as JSON. */
    static JsonArray list(CyRest cyrest) throws IOException {
        CyRest.Reply reply = cyrest.post(NAMESPACE, "list", Map.of());
        assertTrue(reply.ok(), reply.toString());
        return reply.data().getAsJsonArray();
    }

    /** Uninstalls everything, so each test starts from an empty registry. */
    static void uninstallAll(CyRest cyrest) throws IOException {
        for (JsonElement app : list(cyrest)) {
            String name = app.getAsJsonObject().get("name").getAsString();
            CyRest.Reply reply = cyrest.post(NAMESPACE, "uninstall", Map.of("name", name));
            assertTrue(reply.ok(), reply.toString());
        }
    }
}
