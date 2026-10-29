package org.cytoscape.serviceapps.app;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.cytoscape.serviceapps.fixture.ScenarioCatalog;
import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.model.ServiceMetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppSupportTest {

    private static ServiceMetadata scenario(String name) {
        return Json.parse(ScenarioCatalog.get(name).metadata().toString(), ServiceMetadata.class);
    }

    @Test
    void experimentalParameterTypesAndEdgeListsAreUnsupported() {
        assertTrue(
                AppSupport.unsupportedReason(scenario("unsupported-file"), true)
                        .orElseThrow()
                        .contains("'file'"));
        assertTrue(
                AppSupport.unsupportedReason(scenario("unsupported-access-token"), true)
                        .orElseThrow()
                        .contains("'accessToken'"));
        assertTrue(
                AppSupport.unsupportedReason(scenario("unsupported-edgelist"), true)
                        .orElseThrow()
                        .contains("edge list"));
    }

    @Test
    void anNdexUuidParameterIsSupported() {
        assertEquals(
                Optional.empty(), AppSupport.unsupportedReason(scenario("param-ndex-uuid"), true));
    }

    @Test
    void networkInputNeedsCxSupport() {
        assertEquals(Optional.empty(), AppSupport.unsupportedReason(scenario("hello"), true));
        assertTrue(
                AppSupport.unsupportedReason(scenario("hello"), false)
                        .orElseThrow()
                        .contains("CX Support"));
        assertEquals(
                Optional.empty(), AppSupport.unsupportedReason(scenario("input-columns"), false));
    }

    @Test
    void readsTheInputShape() {
        assertEquals("network", AppSupport.inputType(scenario("hello")));
        assertEquals("node", AppSupport.inputType(scenario("input-columns")));
        assertEquals(null, AppSupport.inputType(scenario("input-none")));
        assertEquals("dynamic", AppSupport.scope(scenario("hello")));
        assertEquals("all", AppSupport.scope(scenario("input-columns")));
        assertEquals("dynamic", AppSupport.scope(scenario("input-none")));
    }

    @Test
    void inputTypeNoneMeansNoData() {
        assertEquals(null, AppSupport.inputType(scenario("input-type-none")));
        assertFalse(AppSupport.needsNetwork(scenario("input-type-none")));
        ServiceMetadata shouting = scenario("input-columns");
        shouting.serviceInputDefinition.type = " NONE ";
        assertEquals(null, AppSupport.inputType(shouting));
        assertEquals(Optional.empty(), AppSupport.unsupportedReason(shouting, false));
    }
}
