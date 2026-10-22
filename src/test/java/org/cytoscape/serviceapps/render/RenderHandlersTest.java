package org.cytoscape.serviceapps.render;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
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

    private boolean selected(org.cytoscape.model.CyIdentifiable element) {
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
    void openUrlOpensWhenTargetIsNullOrAbsent() {
        render(ResultAction.OPEN_URL, SentIds.NONE);
        render(
                List.of(
                        new ResultAction(
                                ResultAction.OPEN_URL,
                                JsonParser.parseString("{\"url\":\"https://a.example\"}"))),
                SentIds.NONE);
        RenderReport ignored =
                render(
                        List.of(
                                new ResultAction(
                                        ResultAction.OPEN_URL,
                                        JsonParser.parseString(
                                                "{\"url\":\"https://b.example\",\"target\":\"frame\"}"))),
                        SentIds.NONE);

        verify(browser).openURL("https://www.cytoscape.org");
        verify(browser).openURL("https://a.example");
        verify(browser, never()).openURL("https://b.example");
        assertTrue(ignored.notes().get(0).contains("was not opened"));
    }

    @Test
    void updateTablesCreatesColumnsAndWritesRowsByElementId() {
        RenderReport report = render(ResultAction.UPDATE_TABLES, SentIds.NONE);

        assertEquals(
                Integer.class, target.network.getDefaultNodeTable().getColumn("count").getType());
        assertEquals(10, target.network.getRow(target.node(1)).get("count", Integer.class));
        assertEquals("lowest", target.network.getRow(target.node(1)).get("notes", String.class));
        assertEquals(24, target.network.getRow(target.node(2)).get("count", Integer.class));
        assertNull(target.network.getRow(target.node(7)).get("count", Integer.class));
        assertTrue(report.problems().isEmpty(), report.summary());
    }

    @Test
    void updateTablesAppliesDefaultsSkipsUnknownIdsAndReportsTypeConflicts() {
        target.network.getDefaultNodeTable().createColumn("score", String.class, false);
        JsonElement data =
                JsonParser.parseString(
                        "{\"id\":\"node\",\"columns\":[{\"id\":\"score\",\"type\":\"double\"},"
                                + "{\"id\":\"rank\",\"type\":\"long\",\"default\":0}],"
                                + "\"rows\":{\"0\":{\"score\":1.5,\"rank\":4},\"999\":{\"rank\":1}}}");

        RenderReport report =
                render(List.of(new ResultAction(ResultAction.UPDATE_TABLES, data)), SentIds.NONE);

        assertEquals(
                String.class, target.network.getDefaultNodeTable().getColumn("score").getType());
        assertNull(target.network.getRow(target.node(0)).get("score", String.class));
        assertEquals(4L, target.network.getRow(target.node(0)).get("rank", Long.class));
        assertEquals(0L, target.network.getRow(target.node(1)).get("rank", Long.class));
        assertTrue(report.problems().get(0).contains("'score'"), report.summary());
        assertTrue(report.notes().get(0).contains("1 row(s)"), report.summary());
    }

    @Test
    void updateTablesRejectsOtherTables() {
        RenderReport report =
                render(
                        List.of(
                                new ResultAction(
                                        ResultAction.UPDATE_TABLES,
                                        JsonParser.parseString("{\"id\":\"network\"}"))),
                        SentIds.NONE);

        assertTrue(report.problems().get(0).contains("node or edge table"));
        assertTrue(report.applied().isEmpty());
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
    void addTablesIsSkippedWithANote() {
        RenderReport report = render(ResultAction.ADD_TABLES, SentIds.NONE);

        assertTrue(report.notes().get(0).contains("addTables"));
    }

    @Test
    void unknownActionsAreReportedAndTheRestStillRun() {
        RenderReport report =
                render(
                        List.of(
                                new ResultAction("explodeNetwork", new JsonObject()),
                                new ResultAction(
                                        ResultAction.UPDATE_SELECTION,
                                        ScenarioCatalog.exampleAction("updateSelection")
                                                .get("data"))),
                        SentIds.NONE);

        assertTrue(report.problems().get(0).contains("explodeNetwork"));
        assertTrue(selected(target.node(15)));
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

        assertTrue(e.getMessage().contains("updateSelection"));
    }

    @Test
    void updateNetworkUpdatesInPlaceAndRemovesOnlySentElements() {
        long suid = target.network.getSUID();
        SentIds everything =
                new SentIds(
                        Set.copyOf(ContractNetwork.NODE_IDS),
                        Set.of(ContractNetwork.EDGE_3, ContractNetwork.EDGE_6));

        RenderReport report = render(ResultAction.UPDATE_NETWORK, everything);

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
        assertTrue(report.problems().isEmpty(), report.summary());
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

        RenderReport report =
                render(
                        List.of(new ResultAction(ResultAction.UPDATE_NETWORK, broken)),
                        SentIds.NONE);

        assertTrue(report.problems().get(0).contains("connects nodes that aren't in the result"));
        assertEquals(before, target.snapshot());
    }

    @Test
    void updateNetworkRejectsHcxNetworks() {
        target.network.getDefaultNetworkTable().createColumn("ndexSchema", String.class, false);
        target.network.getRow(target.network).set("ndexSchema", "hierarchy_v0.1");

        RenderReport report = render(ResultAction.UPDATE_NETWORK, SentIds.NONE);

        assertTrue(report.problems().get(0).contains("HCX"));
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
