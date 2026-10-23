package org.cytoscape.serviceapps.render;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.serviceapps.fixture.ScenarioCatalog;
import org.cytoscape.serviceapps.model.ResultAction;
import org.cytoscape.serviceapps.request.SentIds;
import org.cytoscape.serviceapps.testsupport.ContractNetwork;
import org.cytoscape.serviceapps.testsupport.FakeCx2Support;
import org.cytoscape.serviceapps.testsupport.TestNetworks;
import org.cytoscape.util.swing.OpenBrowser;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.work.TaskMonitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Each result action on real networks and views, with the fixture's example data. */
class RenderHandlersTest {

    private final ContractNetwork target = new ContractNetwork("target");
    private final CyNetworkViewManager viewManager = mock(CyNetworkViewManager.class);
    private final OpenBrowser browser = mock(OpenBrowser.class);
    private final CyApplicationManager applicationManager = mock(CyApplicationManager.class);
    private final CyNetworkManager networkManager = TestNetworks.support().getNetworkManager();
    private final FakeCx2Support cx2 =
            new FakeCx2Support(
                    networkManager, viewManager, ContractNetwork.views().getNetworkViewFactory());
    private final RenderServiceAppUI renderer =
            new RenderServiceAppUI(
                    List.of(
                            new SelectionHandler(),
                            new LayoutsHandler(viewManager),
                            new OpenUrlHandler(browser),
                            new TablesHandler(),
                            new AddNetworksHandler(cx2, applicationManager),
                            new AddTablesHandler(),
                            new UpdateNetworkHandler(viewManager)));

    {
        when(viewManager.getNetworkViews(target.network)).thenReturn(List.of(target.view));
    }

    private RenderReport render(String action, SentIds sent) {
        JsonObject example = ScenarioCatalog.exampleAction(action);
        return render(List.of(new ResultAction(action, example.get("data"))), sent);
    }

    private RenderReport render(List<ResultAction> actions, SentIds sent) {
        return renderer.render(
                actions,
                ResultAction.ALL,
                new UIInfo(target.network, null, sent),
                mock(TaskMonitor.class));
    }

    private boolean selected(CyIdentifiable element) {
        return Boolean.TRUE.equals(
                target.network.getRow(element).get(CyNetwork.SELECTED, Boolean.class));
    }

    private static double x(CyNetworkView view, CyNode node) {
        return view.getNodeView(node).getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION);
    }

    @Test
    void updateSelectionSelectsExactlyTheListedElements() {
        target.network.getRow(target.node(1)).set(CyNetwork.SELECTED, true);

        RenderReport report = render(ResultAction.UPDATE_SELECTION, SentIds.NONE);

        for (long id : ContractNetwork.NODE_IDS) {
            assertEquals(
                    Set.of(0L, 15L, 25L).contains(id), selected(target.node(id)), "node " + id);
        }
        assertTrue(selected(target.edgeById(ContractNetwork.EDGE_3)));
        assertTrue(selected(target.edgeById(ContractNetwork.EDGE_6)));
        assertEquals(List.of(ResultAction.UPDATE_SELECTION), report.applied());
    }

    @Test
    void updateLayoutsMovesNodesInEveryViewWithZWhenGiven() {
        CyNetworkView second = target.newView();
        when(viewManager.getNetworkViews(target.network)).thenReturn(List.of(target.view, second));

        render(ResultAction.UPDATE_LAYOUTS, SentIds.NONE);

        for (CyNetworkView view : List.of(target.view, second)) {
            assertEquals(25.2, x(view, target.node(7)));
            assertEquals(
                    -473.6,
                    view.getNodeView(target.node(7))
                            .getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION));
            assertEquals(15.0, x(view, target.node(100)));
            assertEquals(
                    2.0,
                    view.getNodeView(target.node(100))
                            .getVisualProperty(BasicVisualLexicon.NODE_Z_LOCATION));
        }
    }

    @Test
    void updateLayoutsWithATargetViewChangesOnlyThatView() {
        CyNetworkView other = target.newView();
        when(viewManager.getNetworkViews(target.network)).thenReturn(List.of(target.view, other));
        double before = x(other, target.node(7));

        renderer.render(
                List.of(
                        new ResultAction(
                                ResultAction.UPDATE_LAYOUTS,
                                ScenarioCatalog.exampleAction("updateLayouts").get("data"))),
                ResultAction.ALL,
                new UIInfo(target.network, target.view, SentIds.NONE),
                mock(TaskMonitor.class));

        assertEquals(25.2, x(target.view, target.node(7)));
        assertEquals(before, x(other, target.node(7)));
    }

    @Test
    void openUrlOpensTheSystemBrowserWhenTargetIsNullOrAbsent() {
        when(browser.openURL(anyString(), anyBoolean())).thenReturn(true);

        render(ResultAction.OPEN_URL, SentIds.NONE);
        render(
                List.of(
                        new ResultAction(
                                ResultAction.OPEN_URL,
                                JsonParser.parseString("{\"url\":\"https://a.example\"}"))),
                SentIds.NONE);

        verify(browser).openURL("https://www.cytoscape.org", false);
        verify(browser).openURL("https://a.example", false);
    }

    @Test
    void openUrlWithATargetFailsWithoutOpening() {
        RenderException e =
                assertThrows(
                        RenderException.class,
                        () ->
                                render(
                                        List.of(
                                                new ResultAction(
                                                        ResultAction.OPEN_URL,
                                                        JsonParser.parseString(
                                                                "{\"url\":\"https://b.example\",\"target\":\"frame\"}"))),
                                        SentIds.NONE));

        assertTrue(
                e.getMessage().contains("openURL target 'frame' isn't supported"), e.getMessage());
        verify(browser, never()).openURL(anyString(), anyBoolean());
        verify(browser, never()).openURL(anyString());
    }

    @Test
    void openUrlFailsWhenNoBrowserOpens() {
        when(browser.openURL(anyString(), anyBoolean())).thenReturn(false);

        RenderException e =
                assertThrows(
                        RenderException.class, () -> render(ResultAction.OPEN_URL, SentIds.NONE));

        assertEquals(
                "Rendering failed at openURL (1 of 1): no web browser could be opened for"
                        + " https://www.cytoscape.org. Nothing was applied.",
                e.getMessage());
    }

    @Test
    void updateTablesCreatesColumnsAndWritesRowsByElementId() {
        RenderReport report = render(ResultAction.UPDATE_TABLES, SentIds.NONE);

        assertEquals(List.of(ResultAction.UPDATE_TABLES), report.applied());
        assertEquals(
                Integer.class, target.network.getDefaultNodeTable().getColumn("count").getType());
        assertEquals(10, target.network.getRow(target.node(1)).get("count", Integer.class));
        assertEquals("lowest", target.network.getRow(target.node(1)).get("notes", String.class));
        assertEquals(24, target.network.getRow(target.node(2)).get("count", Integer.class));
        assertNull(target.network.getRow(target.node(7)).get("count", Integer.class));
        assertEquals(
                List.of("updateTables skipped 1 row(s) with IDs not in the network"),
                report.notes());
    }

    @Test
    void updateTablesAppliesDefaultsAndSkipsUnknownIds() {
        JsonElement data =
                JsonParser.parseString(
                        "{\"id\":\"node\",\"columns\":[{\"id\":\"rank\",\"type\":\"long\",\"default\":0}],"
                                + "\"rows\":{\"0\":{\"rank\":4},\"999\":{\"rank\":1}}}");

        RenderReport report =
                render(List.of(new ResultAction(ResultAction.UPDATE_TABLES, data)), SentIds.NONE);

        assertEquals(4L, target.network.getRow(target.node(0)).get("rank", Long.class));
        assertEquals(0L, target.network.getRow(target.node(1)).get("rank", Long.class));
        assertTrue(report.notes().get(0).contains("1 row(s)"), report.summary());
    }

    @Test
    void updateTablesWithATypeConflictFailsBeforeWritingAnything() {
        target.network.getDefaultNodeTable().createColumn("score", String.class, false);
        JsonElement data =
                JsonParser.parseString(
                        "{\"id\":\"node\",\"columns\":[{\"id\":\"rank\",\"type\":\"long\",\"default\":0},"
                                + "{\"id\":\"score\",\"type\":\"double\"}],"
                                + "\"rows\":{\"0\":{\"score\":1.5,\"rank\":4}}}");

        RenderException e =
                assertThrows(
                        RenderException.class,
                        () ->
                                render(
                                        List.of(new ResultAction(ResultAction.UPDATE_TABLES, data)),
                                        SentIds.NONE));

        assertEquals(
                "Rendering failed at updateTables (1 of 1): updateTables column 'score' is string in"
                        + " Desktop but double in the result. Nothing was applied.",
                e.getMessage());
        assertNull(target.network.getDefaultNodeTable().getColumn("rank"), "nothing created");
    }

    @Test
    void updateTablesWithAnUnknownTypeFails() {
        JsonElement data =
                JsonParser.parseString(
                        "{\"id\":\"node\",\"columns\":[{\"id\":\"rank\",\"type\":\"long\"},"
                                + "{\"id\":\"shape\",\"type\":\"complex\"}],"
                                + "\"rows\":{\"0\":{\"rank\":1}}}");
        var before = target.snapshot();

        RenderException e =
                assertThrows(
                        RenderException.class,
                        () ->
                                render(
                                        List.of(new ResultAction(ResultAction.UPDATE_TABLES, data)),
                                        SentIds.NONE));

        assertEquals(
                "Rendering failed at updateTables (1 of 1): updateTables column 'shape' has unknown"
                        + " type 'complex'. Nothing was applied.",
                e.getMessage());
        assertEquals(before, target.snapshot(), "not even the valid column is created");
        assertNull(target.network.getDefaultNodeTable().getColumn("rank"));
    }

    @Test
    void aFailureAfterASkippedActionSaysNothingWasApplied() {
        JsonElement otherTable = JsonParser.parseString("{\"id\":\"network\",\"rows\":{}}");
        ResultAction skipped =
                new ResultAction(
                        ResultAction.ADD_TABLES,
                        ScenarioCatalog.exampleAction(ResultAction.ADD_TABLES).get("data"));

        RenderException e =
                assertThrows(
                        RenderException.class,
                        () ->
                                render(
                                        List.of(
                                                skipped,
                                                new ResultAction(
                                                        ResultAction.UPDATE_TABLES, otherTable)),
                                        SentIds.NONE));

        assertEquals(
                "Rendering failed at updateTables (2 of 2): updateTables can only update the node or"
                        + " edge table, not 'network'. Nothing was applied.",
                e.getMessage());
    }

    @Test
    void updateTablesRejectsOtherTables() {
        RenderException e =
                assertThrows(
                        RenderException.class,
                        () ->
                                render(
                                        List.of(
                                                new ResultAction(
                                                        ResultAction.UPDATE_TABLES,
                                                        JsonParser.parseString(
                                                                "{\"id\":\"network\"}"))),
                                        SentIds.NONE));

        assertTrue(e.getMessage().contains("node or edge table"), e.getMessage());
    }

    @Test
    void addNetworksImportsEachAsANewCollectionAndMakesTheFirstCurrent() {
        int before = networkManager.getNetworkSet().size();

        render(ResultAction.ADD_NETWORKS, SentIds.NONE);

        assertEquals(before + 1, networkManager.getNetworkSet().size());
        verify(applicationManager).setCurrentNetwork(any(CyNetwork.class));
        verify(viewManager).addNetworkView(any(CyNetworkView.class));
    }

    @Test
    void addTablesIsSkippedWithANoteAndNotListedAsApplied() {
        RenderReport report = render(ResultAction.ADD_TABLES, SentIds.NONE);

        assertEquals(List.of("addTables is not supported yet and was skipped"), report.notes());
        assertTrue(report.applied().isEmpty());
    }

    @Test
    void anUnknownActionFailsBeforeAnythingIsApplied() {
        RenderException e =
                assertThrows(
                        RenderException.class,
                        () ->
                                render(
                                        List.of(
                                                new ResultAction(
                                                        ResultAction.UPDATE_SELECTION,
                                                        ScenarioCatalog.exampleAction(
                                                                        "updateSelection")
                                                                .get("data")),
                                                new ResultAction(
                                                        "explodeNetwork", new JsonObject())),
                                        SentIds.NONE));

        assertEquals(
                "Rendering failed: the result asks for unknown action 'explodeNetwork'. Nothing was"
                        + " applied.",
                e.getMessage());
        assertFalse(selected(target.node(15)), "even the action before it is not applied");
    }

    @Test
    void theFirstFailingActionStopsTheRun() {
        RenderException e =
                assertThrows(
                        RenderException.class,
                        () ->
                                render(
                                        List.of(
                                                new ResultAction(
                                                        ResultAction.UPDATE_LAYOUTS,
                                                        ScenarioCatalog.exampleAction(
                                                                        "updateLayouts")
                                                                .get("data")),
                                                new ResultAction(
                                                        ResultAction.UPDATE_TABLES,
                                                        JsonParser.parseString(
                                                                "{\"id\":\"network\"}")),
                                                new ResultAction(
                                                        ResultAction.UPDATE_SELECTION,
                                                        ScenarioCatalog.exampleAction(
                                                                        "updateSelection")
                                                                .get("data"))),
                                        SentIds.NONE));

        assertEquals(
                "Rendering failed at updateTables (2 of 3): updateTables can only update the node or"
                        + " edge table, not 'network'. Applied: updateLayouts. Not applied:"
                        + " updateSelection.",
                e.getMessage());
        assertEquals(25.2, x(target.view, target.node(7)));
        assertFalse(selected(target.node(15)));
    }

    @Test
    void malformedLayoutDataFailsBeforeAnyNodeMoves() {
        double before = x(target.view, target.node(7));

        RenderException e =
                assertThrows(
                        RenderException.class,
                        () ->
                                render(
                                        List.of(
                                                new ResultAction(
                                                        ResultAction.UPDATE_LAYOUTS,
                                                        JsonParser.parseString(
                                                                "[{\"id\":7,\"x\":1,\"y\":2},{\"id\":100}]"))),
                                        SentIds.NONE));

        assertTrue(e.getMessage().contains("needs a numeric id, x and y"), e.getMessage());
        assertEquals(before, x(target.view, target.node(7)));
    }

    @Test
    void anActionNeedingANetworkFailsTheRunWhenNoneIsOpen() {
        RenderException e =
                assertThrows(
                        RenderException.class,
                        () ->
                                renderer.render(
                                        List.of(
                                                new ResultAction(
                                                        ResultAction.UPDATE_SELECTION,
                                                        new JsonObject())),
                                        ResultAction.ALL,
                                        new UIInfo(null, null, SentIds.NONE),
                                        mock(TaskMonitor.class)));

        assertEquals(
                "Rendering failed: the result asks to updateSelection, which needs a network, and no"
                        + " network was targeted. Nothing was applied.",
                e.getMessage());
    }

    @Test
    void updateNetworkUpdatesInPlaceAndRemovesOnlySentElements() {
        long suid = target.network.getSUID();
        SentIds everything =
                new SentIds(
                        Set.copyOf(ContractNetwork.NODE_IDS),
                        Set.of(ContractNetwork.EDGE_3, ContractNetwork.EDGE_6));

        RenderReport report = render(ResultAction.UPDATE_NETWORK, everything);

        assertEquals(List.of("updateNetwork removed 5 node(s) and 2 edge(s)"), report.notes());
        assertEquals(suid, target.network.getSUID());
        assertEquals(2, target.network.getNodeCount());
        assertEquals(1, target.network.getEdgeCount());
        CyNode n0 = target.node(0);
        assertTrue(target.network.containsNode(n0), "node 0 is kept, not recreated");
        assertEquals("node 1", target.network.getRow(n0).get(CyNetwork.NAME, String.class));
        assertEquals(5, target.network.getRow(n0).get("age", Integer.class));
        CyEdge added = target.network.getEdgeList().get(0);
        assertEquals(0.3, target.network.getRow(added).get("weight", Double.class));
        assertEquals(
                "network name",
                target.network.getRow(target.network).get(CyNetwork.NAME, String.class));
    }

    @Test
    void updateNetworkOnASelectionKeepsWhatWasNotSent() {
        SentIds selection = new SentIds(Set.of(0L, 1L, 7L), Set.of());

        render(ResultAction.UPDATE_NETWORK, selection);

        assertFalse(target.network.containsNode(target.node(7)), "sent and absent: removed");
        for (long id : List.of(2L, 15L, 25L, 100L)) {
            assertTrue(target.network.containsNode(target.node(id)), "not sent: kept " + id);
        }
        assertTrue(target.network.containsEdge(target.edgeById(ContractNetwork.EDGE_3)));
    }

    @Test
    void updateNetworkKeepsTheNameWhenTheResultHasNone() {
        JsonElement noName =
                JsonParser.parseString(
                        "[{\"CXVersion\":\"2.0\"},{\"nodes\":[{\"id\":0,\"v\":{\"age\":1}}]}]");

        render(List.of(new ResultAction(ResultAction.UPDATE_NETWORK, noName)), SentIds.NONE);

        assertEquals(
                "target", target.network.getRow(target.network).get(CyNetwork.NAME, String.class));
    }

    @Test
    void updateNetworkRejectsBrokenCx2WithoutChangingAnything() {
        var before = target.snapshot();
        JsonElement broken =
                JsonParser.parseString(
                        "[{\"CXVersion\":\"2.0\"},{\"nodes\":[{\"id\":0}]},{\"edges\":[{\"id\":9,\"s\":0,\"t\":42}]}]");

        RenderException e =
                assertThrows(
                        RenderException.class,
                        () ->
                                render(
                                        List.of(
                                                new ResultAction(
                                                        ResultAction.UPDATE_NETWORK, broken)),
                                        SentIds.NONE));

        assertTrue(e.getMessage().contains("connects nodes that aren't in the result"));
        assertEquals(before, target.snapshot());
    }

    @Test
    void updateNetworkSkipsHcxNetworksWithANote() {
        target.network.getDefaultNetworkTable().createColumn("ndexSchema", String.class, false);
        target.network.getRow(target.network).set("ndexSchema", "hierarchy_v0.1");

        var before = target.snapshot();

        RenderReport report = render(ResultAction.UPDATE_NETWORK, SentIds.NONE);

        assertEquals(
                List.of("updateNetwork skipped: HCX networks can't be updated in place yet"),
                report.notes());
        assertTrue(report.applied().isEmpty());
        assertEquals(before, target.snapshot());
    }

    @Test
    void updateNetworkWithATypeConflictFailsWithTheNetworkUnchanged() {
        target.network.getDefaultNodeTable().createColumn("age", String.class, false);
        var before = target.snapshot();
        SentIds everything =
                new SentIds(
                        Set.copyOf(ContractNetwork.NODE_IDS),
                        Set.of(ContractNetwork.EDGE_3, ContractNetwork.EDGE_6));

        RenderException e =
                assertThrows(
                        RenderException.class,
                        () -> render(ResultAction.UPDATE_NETWORK, everything));

        assertEquals(
                "Rendering failed at updateNetwork (1 of 1): updateNetwork attribute 'age' is string"
                        + " in Desktop but integer in the result. Nothing was applied.",
                e.getMessage());
        assertEquals(before, target.snapshot());
    }

    @Test
    void updateNetworkFillsDeclaredDefaultsAndResolvesAliases() {
        JsonElement data =
                JsonParser.parseString(
                        "[{\"CXVersion\":\"2.0\"},{\"attributeDeclarations\":[{\"nodes\":{"
                                + "\"score\":{\"d\":\"double\",\"v\":0.5},"
                                + "\"cluster label\":{\"d\":\"string\",\"a\":\"cl\"},"
                                + "\"unset\":{\"d\":\"integer\"}}}]},"
                                + "{\"nodes\":[{\"id\":0,\"v\":{\"score\":2.0,\"cl\":\"A\"}},"
                                + "{\"id\":1}]}]");

        render(List.of(new ResultAction(ResultAction.UPDATE_NETWORK, data)), SentIds.NONE);

        CyRow n0 = target.network.getRow(target.node(0));
        CyRow n1 = target.network.getRow(target.node(1));
        assertEquals(2.0, n0.get("score", Double.class), "a given value wins");
        assertEquals(0.5, n1.get("score", Double.class), "an omitted value takes the default");
        assertEquals("A", n0.get("cluster label", String.class), "the alias names the column");
        assertNull(target.network.getDefaultNodeTable().getColumn("cl"));
        assertNull(n1.get("cluster label", String.class), "no default declared: left empty");
        assertNull(target.network.getDefaultNodeTable().getColumn("unset"));
    }

    @Test
    void updateNetworkWithAnUnknownDeclaredTypeFails() {
        JsonElement data =
                JsonParser.parseString(
                        "[{\"CXVersion\":\"2.0\"},{\"attributeDeclarations\":[{\"nodes\":{\"shape\":{\"d\":\"complex\"}}}]},"
                                + "{\"nodes\":[{\"id\":0}]}]");

        var before = target.snapshot();

        RenderException e =
                assertThrows(
                        RenderException.class,
                        () ->
                                render(
                                        List.of(
                                                new ResultAction(
                                                        ResultAction.UPDATE_NETWORK, data)),
                                        SentIds.NONE));

        assertEquals(
                "Rendering failed at updateNetwork (1 of 1): updateNetwork data declares attribute"
                        + " 'shape' with unknown type 'complex'. Nothing was applied.",
                e.getMessage());
        assertEquals(before, target.snapshot());
    }

    @Test
    void multipleActionsApplyInOrder() {
        RenderReport report =
                render(
                        List.of(
                                new ResultAction(
                                        ResultAction.UPDATE_TABLES,
                                        ScenarioCatalog.exampleAction("updateTables").get("data")),
                                new ResultAction(
                                        ResultAction.UPDATE_LAYOUTS,
                                        ScenarioCatalog.exampleAction("updateLayouts").get("data")),
                                new ResultAction(
                                        ResultAction.UPDATE_SELECTION,
                                        ScenarioCatalog.exampleAction("updateSelection")
                                                .get("data"))),
                        SentIds.NONE);

        assertEquals(
                List.of(
                        ResultAction.UPDATE_TABLES,
                        ResultAction.UPDATE_LAYOUTS,
                        ResultAction.UPDATE_SELECTION),
                report.applied());
    }
}
