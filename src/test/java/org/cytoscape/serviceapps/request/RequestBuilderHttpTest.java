package org.cytoscape.serviceapps.request;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.serviceapps.client.JdkHttpTransport;
import org.cytoscape.serviceapps.client.ServiceAppClient;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.FixtureServerExtension;
import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.ndex.NdexLink;
import org.cytoscape.serviceapps.ndex.NetworkEditTracker;
import org.cytoscape.serviceapps.testsupport.FakeCx2Support;
import org.cytoscape.serviceapps.testsupport.TestNdex;
import org.cytoscape.serviceapps.testsupport.TestNetworks;
import org.cytoscape.view.model.CyNetworkViewManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/** What the service receives is exactly the built request: checked in the fixture's request log. */
@ExtendWith(FixtureServerExtension.class)
class RequestBuilderHttpTest {

    private JsonObject submitAndRead(
            FixtureServer fixture, String scenario, ServiceInput input, CyNetwork network) {
        return submitAndRead(fixture, scenario, input, network, TestNdex.link());
    }

    private JsonObject submitAndRead(
            FixtureServer fixture,
            String scenario,
            ServiceInput input,
            CyNetwork network,
            NdexLink ndex) {
        fixture.clearRequests();
        ServiceMetadata meta = TestNetworks.scenario(scenario);
        RequestBuilder builder =
                new RequestBuilder(
                        new FakeCx2Support(
                                TestNetworks.support().getNetworkManager(),
                                mock(CyNetworkViewManager.class),
                                null),
                        ndex);
        BuiltRequest built = builder.build(meta, input, network, null);
        new ServiceAppClient(
                        fixture.url(scenario),
                        new JdkHttpTransport(Duration.ofSeconds(5), Duration.ofSeconds(10)))
                .submit(built.request());

        JsonObject received =
                JsonParser.parseString(fixture.requests().get(0).body()).getAsJsonObject();
        assertEquals(Json.tree(built.request()), received);
        return received;
    }

    @Test
    void parametersArriveAsTextUnderTheirDisplayNames(FixtureServer fixture) {
        JsonObject received =
                submitAndRead(
                        fixture,
                        "param-checkbox-true",
                        new ServiceInput(Map.of("Weighted", "false"), Map.of()),
                        TestNetworks.withEveryColumnType());

        assertEquals("Fixture param-checkbox-true", received.get("algorithm").getAsString());
        JsonElement weighted = received.getAsJsonObject("parameters").get("Weighted");
        assertEquals("false", weighted.getAsString());
        assertEquals(true, weighted.getAsJsonPrimitive().isString());
    }

    @Test
    void tableInputArrivesWithItsColumnsAndRows(FixtureServer fixture) {
        CyNetwork network = TestNetworks.withEveryColumnType();

        JsonObject received =
                submitAndRead(
                        fixture,
                        "input-columns",
                        new ServiceInput(
                                Map.of("Greeting", "hi"),
                                Map.of(
                                        "Gene",
                                        List.of("name"),
                                        "Scores",
                                        List.of("c_double"),
                                        "Any",
                                        List.of("c_long"))),
                        network);

        JsonObject data = received.getAsJsonObject("data");
        assertEquals(
                4, data.getAsJsonArray("columns").size(), "name, c_double, c_long, shared name");
        assertEquals(2, data.getAsJsonObject("rows").size());
    }

    @Test
    void anNdexUuidParameterArrivesAsTheNetworksNdexUrlThenEmptyOnceEdited(FixtureServer fixture) {
        CyNetwork network = TestNetworks.withEveryColumnType();
        CyTable hidden = network.getTable(CyNetwork.class, CyNetwork.HIDDEN_ATTRS);
        hidden.createColumn(NdexLink.UUID_COLUMN, String.class, false);
        hidden.getRow(network.getSUID()).set(NdexLink.UUID_COLUMN, "u-1");
        NetworkEditTracker edits =
                new NetworkEditTracker(TestNetworks.support().getNetworkTableManager());
        edits.markSaved(network);
        NdexLink ndex = new NdexLink(() -> "https://ndex.example/", edits::edited);
        ServiceInput input = new ServiceInput(Map.of("K", "10"), Map.of());

        JsonObject saved = submitAndRead(fixture, "param-ndex-uuid", input, network, ndex);
        network.addNode();
        JsonObject edited = submitAndRead(fixture, "param-ndex-uuid", input, network, ndex);

        assertEquals(
                "https://ndex.example/v3/networks/u-1",
                saved.getAsJsonObject("parameters").get("NDEx network").getAsString());
        assertEquals("", edited.getAsJsonObject("parameters").get("NDEx network").getAsString());
        assertEquals("10", edited.getAsJsonObject("parameters").get("K").getAsString());
    }
}
