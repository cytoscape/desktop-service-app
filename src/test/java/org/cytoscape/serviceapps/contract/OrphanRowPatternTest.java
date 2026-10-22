package org.cytoscape.serviceapps.contract;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only Desktop's known orphan-row shape is left out of the Docker harness's reads. */
class OrphanRowPatternTest {

    private static JsonObject row(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void aRowWithOnlyItsSuidAndSelectedFalseIsAnOrphan() {
        assertTrue(DockerTaskCallHarness.isOrphan(row("{\"SUID\":8489,\"selected\":false}")));
        assertTrue(
                DockerTaskCallHarness.isOrphan(
                        row("{\"SUID\":8489,\"selected\":false,\"name\":null}")));
        assertTrue(DockerTaskCallHarness.isOrphan(row("{\"SUID\":8489}")));
    }

    @Test
    void anyOtherValueMeansItIsNotTheKnownPattern() {
        assertFalse(DockerTaskCallHarness.isOrphan(row("{\"SUID\":8489,\"selected\":true}")));
        assertFalse(
                DockerTaskCallHarness.isOrphan(
                        row("{\"SUID\":8489,\"selected\":false,\"name\":\"n7\"}")));
        assertFalse(
                DockerTaskCallHarness.isOrphan(
                        row("{\"SUID\":8489,\"selected\":false,\"cxid\":7}")));
    }
}
