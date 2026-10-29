package org.cytoscape.serviceapps.request;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.serviceapps.form.FormSpecBuilder;
import org.cytoscape.serviceapps.model.Parameter;
import org.cytoscape.serviceapps.model.ServiceInputDefinition;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.ndex.NdexLink;
import org.cytoscape.serviceapps.testsupport.FakeCx2Support;
import org.cytoscape.serviceapps.testsupport.TestNdex;
import org.cytoscape.serviceapps.testsupport.TestNetworks;
import org.cytoscape.view.model.CyNetworkViewManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Request bodies for every input type, scope and network model. */
class RequestBuilderTest {

    private final FakeCx2Support cx2 =
            new FakeCx2Support(
                    TestNetworks.support().getNetworkManager(),
                    mock(CyNetworkViewManager.class),
                    null);
    private final RequestBuilder builder = new RequestBuilder(cx2, TestNdex.link());

    /** a - b - c, edges ab and bc; node a and edge bc may be selected. */
    private final CyNetwork network = TestNetworks.empty();

    private final CyNode a = network.addNode();
    private final CyNode b = network.addNode();
    private final CyNode c = network.addNode();
    private final CyEdge ab = network.addEdge(a, b, true);
    private final CyEdge bc = network.addEdge(b, c, true);

    {
        network.getDefaultNodeTable().createColumn("score", Double.class, false);
        network.getDefaultNodeTable().createListColumn("tags", String.class, false);
        network.getRow(a).set(CyNetwork.NAME, "A");
        network.getRow(a).set("score", 1.5);
        network.getRow(a).set("tags", List.of("x", "y"));
        network.getRow(b).set(CyNetwork.NAME, "B");
        network.getDefaultEdgeTable().createColumn("weight", Double.class, false);
        network.getRow(bc).set("weight", 0.3);
    }

    private static ServiceMetadata meta(String type, String scope) {
        ServiceMetadata meta = new ServiceMetadata();
        meta.name = "Hello World";
        meta.serviceInputDefinition = new ServiceInputDefinition();
        meta.serviceInputDefinition.type = type;
        meta.serviceInputDefinition.scope = scope;
        return meta;
    }

    private static ServiceMetadata network(String scope, String model) {
        ServiceMetadata meta = meta("network", scope);
        meta.serviceInputDefinition.inputNetwork = new ServiceInputDefinition.InputNetwork();
        meta.serviceInputDefinition.inputNetwork.model = model;
        meta.serviceInputDefinition.inputNetwork.format = "cx2";
        return meta;
    }

    private static ServiceMetadata columns(String type, String scope, String autoColumn) {
        ServiceMetadata meta = meta(type, scope);
        ServiceInputDefinition.InputColumn chosen = new ServiceInputDefinition.InputColumn();
        chosen.name = "Values";
        ServiceInputDefinition.InputColumn auto = new ServiceInputDefinition.InputColumn();
        auto.columnName = autoColumn;
        meta.serviceInputDefinition.inputColumns = List.of(chosen, auto);
        return meta;
    }

    private void select(CyIdentifiable... elements) {
        for (var element : elements) {
            network.getRow(element).set(CyNetwork.SELECTED, true);
        }
    }

    private Set<String> rowKeys(JsonElement data) {
        return new HashSet<>(data.getAsJsonObject().getAsJsonObject("rows").keySet());
    }

    private String id(CyIdentifiable element) {
        return Long.toString(IdMapper.of(network).id(element));
    }

    @Test
    void noInputSendsAnEmptyObjectAndTheParametersAsText() {
        ServiceMetadata meta = new ServiceMetadata();
        meta.name = "Hello World";

        BuiltRequest built =
                builder.build(
                        meta, new ServiceInput(Map.of("Weighted", "true"), Map.of()), null, null);

        assertEquals("Hello World", built.request().algorithm);
        assertEquals(new JsonObject(), built.request().data);
        assertEquals(Map.of("Weighted", "true"), built.request().parameters);
        assertEquals(SentIds.NONE, built.sent());
    }

    @Test
    void inputTypeNoneSendsAnEmptyObjectEvenWithAnOpenNetwork() {
        ServiceMetadata meta = columns("node", "all", "name");
        meta.serviceInputDefinition.type = "none";

        BuiltRequest built =
                builder.build(meta, new ServiceInput(Map.of(), Map.of()), network, null);

        assertEquals(new JsonObject(), built.request().data);
        assertEquals(SentIds.NONE, built.sent());
    }

    @Test
    void parametersThatShareANameAreSentUnderTheirGroupKeys() {
        ServiceMetadata meta = TestNetworks.scenario("param-groups");
        meta.serviceInputDefinition = null;
        ServiceInput input =
                new FormSpecBuilder()
                        .build(meta, new JsonObject(), null)
                        .initialValues()
                        .toServiceInput();

        Map<String, String> sent = builder.build(meta, input, null, null).request().parameters;

        assertEquals("n", sent.get("Nodes/Gap"));
        assertEquals("c", sent.get("Clusters/Gap"));
        assertFalse(sent.containsKey("Gap"));
    }

    @Test
    void nodeTableInputSendsTheChosenAndAutoColumns() {
        ServiceInput input = new ServiceInput(Map.of(), Map.of("Values", List.of("score", "tags")));

        BuiltRequest built = builder.build(columns("node", "all", "name"), input, network, null);

        JsonObject data = built.request().data.getAsJsonObject();
        JsonArray declared = data.getAsJsonArray("columns");
        assertEquals("score", declared.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("double", declared.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("list_of_string", declared.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("name", declared.get(2).getAsJsonObject().get("id").getAsString());
        assertEquals("string", declared.get(2).getAsJsonObject().get("type").getAsString());
        JsonObject rowA = data.getAsJsonObject("rows").getAsJsonObject(id(a));
        assertEquals(1.5, rowA.get("score").getAsDouble());
        assertEquals("y", rowA.getAsJsonArray("tags").get(1).getAsString());
        assertEquals("A", rowA.get("name").getAsString());
        assertEquals(Set.of(id(a), id(b), id(c)), rowKeys(data));
        assertEquals(3, built.sent().nodes().size());
        assertTrue(built.sent().edges().isEmpty());
    }

    @Test
    void dynamicScopeSendsTheSelectionWhenThereIsOne() {
        ServiceInput input = new ServiceInput(Map.of(), Map.of("Values", List.of("score")));
        ServiceMetadata meta = columns("node", "dynamic", "name");

        assertEquals(3, rowKeys(builder.build(meta, input, network, null).request().data).size());
        select(a);
        assertEquals(
                Set.of(id(a)), rowKeys(builder.build(meta, input, network, null).request().data));
    }

    @Test
    void selectedScopeSendsOnlyTheSelectionAndAllIgnoresIt() {
        ServiceInput input = new ServiceInput(Map.of(), Map.of("Values", List.of("score")));
        select(a);

        assertEquals(
                Set.of(id(a)),
                rowKeys(
                        builder.build(columns("node", "selected", "name"), input, network, null)
                                .request()
                                .data));
        assertEquals(
                3,
                rowKeys(
                                builder.build(columns("node", "all", "name"), input, network, null)
                                        .request()
                                        .data)
                        .size());
    }

    @Test
    void edgeTableInputSendsEdgeRows() {
        ServiceInput input = new ServiceInput(Map.of(), Map.of("Values", List.of("weight")));
        select(bc);

        BuiltRequest built =
                builder.build(columns("edge", "selected", "interaction"), input, network, null);

        assertEquals(Set.of(id(bc)), rowKeys(built.request().data));
        assertEquals(
                0.3,
                built.request()
                        .data
                        .getAsJsonObject()
                        .getAsJsonObject("rows")
                        .getAsJsonObject(id(bc))
                        .get("weight")
                        .getAsDouble());
        assertEquals(Set.of(IdMapper.of(network).id(bc)), built.sent().edges());
    }

    @Test
    void aMissingAutoIncludedColumnFailsAndNamesIt() {
        ServiceInput input = new ServiceInput(Map.of(), Map.of("Values", List.of("score")));

        RequestException e =
                assertThrows(
                        RequestException.class,
                        () ->
                                builder.build(
                                        columns("node", "all", "gene symbol"),
                                        input,
                                        network,
                                        null));

        assertTrue(e.getMessage().contains("'gene symbol'"), e.getMessage());
    }

    @Test
    void wholeNetworkInputExportsTheNetworkWithItsView() {
        BuiltRequest built =
                builder.build(network("all", "network"), ServiceInput.empty(), network, null);

        assertEquals(1, cx2.exports.size());
        assertEquals(network, cx2.exports.get(0).network());
        assertTrue(built.request().data.isJsonArray());
        assertEquals(3, built.sent().nodes().size());
        assertEquals(2, built.sent().edges().size());
    }

    @Test
    void aSelectionIsExportedFromATemporarySubnetworkWithEdgeEndpoints() {
        CyRootNetwork root = ((CySubNetwork) network).getRootNetwork();
        int subnetworks = root.getSubNetworkList().size();
        select(a, bc);

        BuiltRequest built =
                builder.build(network("dynamic", "network"), ServiceInput.empty(), network, null);

        FakeCx2Support.Export export = cx2.exports.get(0);
        assertFalse(export.network() == network);
        assertNull(export.view());
        assertEquals(3, export.nodes(), "a, plus b and c as bc's endpoints");
        assertEquals(1, export.edges());
        assertEquals(subnetworks, root.getSubNetworkList().size(), "the subnetwork is removed");
        assertEquals(3, built.sent().nodes().size());
        assertEquals(Set.of(IdMapper.of(network).id(bc)), built.sent().edges());
    }

    @Test
    void graphModelSendsIdsOnly() {
        BuiltRequest built =
                builder.build(network("all", "graph"), ServiceInput.empty(), network, null);

        JsonArray cx2Doc = built.request().data.getAsJsonArray();
        assertEquals("2.0", cx2Doc.get(0).getAsJsonObject().get("CXVersion").getAsString());
        JsonArray nodes = cx2Doc.get(2).getAsJsonObject().getAsJsonArray("nodes");
        JsonArray edges = cx2Doc.get(3).getAsJsonObject().getAsJsonArray("edges");
        assertEquals(3, nodes.size());
        assertEquals(1, nodes.get(0).getAsJsonObject().size(), "ids only");
        JsonObject abEntry = null;
        for (JsonElement edge : edges) {
            if (edge.getAsJsonObject().get("id").getAsString().equals(id(ab))) {
                abEntry = edge.getAsJsonObject();
            }
        }
        assertEquals(Long.parseLong(id(a)), abEntry.get("s").getAsLong());
        assertEquals(Long.parseLong(id(b)), abEntry.get("t").getAsLong());
        assertTrue(cx2.exports.isEmpty(), "graph model doesn't need CX Support");
    }

    @Test
    void networkInputNeedsAnOpenNetwork() {
        assertThrows(
                RequestException.class,
                () -> builder.build(network("all", "network"), ServiceInput.empty(), null, null));
    }

    private static Parameter parameter(String displayName, String type) {
        Parameter parameter = new Parameter();
        parameter.displayName = displayName;
        parameter.type = type;
        return parameter;
    }

    private static final NdexLink.Value LINKED =
            new NdexLink.Value("https://ndex.example/v3/networks/u-1", null);

    /** An NDEx link answering {@code value} for every network, recording what it is asked. */
    private static NdexLink answering(NdexLink.Value value, List<CyNetwork> asked) {
        return new NdexLink(() -> "https://ndex.example", n -> false, TestNdex.FOUND) {
            @Override
            public Value value(CyNetwork n) {
                asked.add(n);
                return value;
            }
        };
    }

    @Test
    void anNdexUuidParameterCarriesTheTargetsNdexUrlWhateverTheInputSays() {
        ServiceMetadata meta = network("all", "network");
        meta.parameters =
                List.of(parameter("Interactome", Parameter.NDEX_UUID), parameter("K", "text"));

        BuiltRequest built =
                new RequestBuilder(cx2, answering(LINKED, new ArrayList<>()))
                        .build(
                                meta,
                                new ServiceInput(
                                        Map.of("K", "10", "Interactome", "typed by a caller"),
                                        Map.of()),
                                network,
                                null);

        assertEquals(
                Map.of("Interactome", "https://ndex.example/v3/networks/u-1", "K", "10"),
                built.request().parameters);
        assertEquals(List.of(), built.notes());
    }

    @Test
    void anEmptyNdexValueSendsTheNetworkAnywayAndCarriesItsNote() {
        ServiceMetadata meta = network("all", "network");
        meta.parameters = List.of(parameter("Interactome", Parameter.NDEX_UUID));
        NdexLink.Value empty = new NdexLink.Value("", NdexLink.EMPTY_NOTE + "the reason");

        BuiltRequest built =
                new RequestBuilder(cx2, answering(empty, new ArrayList<>()))
                        .build(meta, ServiceInput.empty(), network, null);

        assertEquals(Map.of("Interactome", ""), built.request().parameters);
        assertTrue(built.request().data.isJsonArray(), "the network is still sent as data");
        assertEquals(List.of(NdexLink.EMPTY_NOTE + "the reason"), built.notes());
    }

    @Test
    void anAppWithNoInputSendsAnEmptyNdexValueWhenNoNetworkIsOpen() {
        ServiceMetadata meta = meta(null, null);
        meta.parameters = List.of(parameter("Interactome", Parameter.NDEX_UUID));

        BuiltRequest built =
                new RequestBuilder(cx2, TestNdex.link())
                        .build(meta, ServiceInput.empty(), null, null);

        assertEquals(Map.of("Interactome", ""), built.request().parameters);
        assertEquals(List.of(NdexLink.EMPTY_NOTE + "no network is open"), built.notes());
    }

    @Test
    void severalNdexUuidParametersShareOneValue() {
        ServiceMetadata meta = network("all", "network");
        meta.parameters =
                List.of(
                        parameter("Interactome", Parameter.NDEX_UUID),
                        parameter("Reference", Parameter.NDEX_UUID));
        List<CyNetwork> asked = new ArrayList<>();

        BuiltRequest built =
                new RequestBuilder(cx2, answering(LINKED, asked))
                        .build(meta, ServiceInput.empty(), network, null);

        assertEquals(
                Map.of(
                        "Interactome", "https://ndex.example/v3/networks/u-1",
                        "Reference", "https://ndex.example/v3/networks/u-1"),
                built.request().parameters);
        assertEquals(List.of(network), asked);
    }

    @Test
    void anAppWithoutAnNdexUuidParameterNeverAsksAndHasNoNotes() {
        List<CyNetwork> asked = new ArrayList<>();

        BuiltRequest built =
                new RequestBuilder(cx2, answering(LINKED, asked))
                        .build(network("all", "network"), ServiceInput.empty(), network, null);

        assertEquals(List.of(), asked);
        assertEquals(List.of(), built.notes());
    }
}
