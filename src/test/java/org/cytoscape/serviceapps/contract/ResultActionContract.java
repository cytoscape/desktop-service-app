package org.cytoscape.serviceapps.contract;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.cytoscape.serviceapps.contract.ContractHarness.Outcome;
import org.cytoscape.serviceapps.contract.ContractHarness.Ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The result action rendering contract (#44 test spec B), written once against {@link
 * ContractHarness}. Each scenario installs a fixture app whose result is the spec's example for one
 * action, runs it, and checks Desktop's state. The second network must never change.
 */
public abstract class ResultActionContract {

    private static final List<Long> NODES = List.of(0L, 1L, 2L, 7L, 15L, 25L, 100L);

    protected abstract ContractHarness harness();

    private ContractHarness h;
    private Map<String, Object> otherBefore;

    @BeforeEach
    void resetDesktop() {
        h = harness();
        h.reset();
        otherBefore = h.otherState();
    }

    @AfterEach
    void theSecondNetworkIsUnchanged() {
        assertEquals(otherBefore, h.otherState(), "the second open network changed");
    }

    private Outcome succeed(String scenario, Ui ui) {
        Outcome outcome = h.run(scenario, ui);
        assertTrue(outcome.succeeded(), scenario + ": " + outcome.message());
        return outcome;
    }

    @Test
    void updateSelectionSelectsExactlyTheListedElements() {
        h.select(1);

        succeed("result-update-selection", Ui.CURRENT_VIEW);

        for (long id : NODES) {
            assertEquals(Set.of(0L, 15L, 25L).contains(id), h.nodeSelected(id), "node " + id);
        }
        assertTrue(h.edgeSelected(3));
        assertTrue(h.edgeSelected(6));
    }

    @Test
    void updateLayoutsMovesEveryViewWhenNoViewIsCurrent() {
        h.addTargetView();

        succeed("result-update-layouts", Ui.NO_CURRENT_VIEW);

        for (int view = 0; view < 2; view++) {
            assertArrayEquals(new double[] {25.2, -473.6}, xy(view, 7), "view " + view);
            assertArrayEquals(
                    new double[] {15.0, 100.0, 2.0}, h.position(view, 100), "view " + view);
        }
    }

    @Test
    void updateLayoutsWithATargetViewChangesOnlyThatView() {
        h.addTargetView();
        double[] secondBefore = h.position(1, 7);

        succeed("result-update-layouts", Ui.CURRENT_VIEW);

        assertArrayEquals(new double[] {25.2, -473.6}, xy(0, 7));
        assertArrayEquals(secondBefore, h.position(1, 7));
    }

    private double[] xy(int view, long node) {
        double[] p = h.position(view, node);
        return new double[] {p[0], p[1]};
    }

    @Test
    void openUrlOpensTheBrowserOnceWhenTargetIsNull() {
        succeed("result-open-url", Ui.CURRENT_VIEW);

        assertEquals(List.of("https://www.cytoscape.org"), h.openedUrls());
    }

    @Test
    void openUrlOpensTheBrowserOnceWhenTargetIsAbsent() {
        succeed("result-open-url-no-target", Ui.CURRENT_VIEW);

        assertEquals(List.of("https://www.cytoscape.org"), h.openedUrls());
    }

    @Test
    void updateTablesCreatesColumnsWritesRowsByIdAndCountsUnknownIds() {
        Outcome outcome = succeed("result-update-tables", Ui.CURRENT_VIEW);

        assertEquals(Integer.class, h.nodeColumnType("count"));
        assertEquals(10, h.nodeValue("count", 1));
        assertEquals("lowest", h.nodeValue("notes", 1));
        assertEquals(24, h.nodeValue("count", 2));
        assertNull(h.nodeValue("count", 7));
        assertTrue(outcome.message().contains("1 row(s)"), outcome.message());
    }

    @Test
    void updateTablesReportsATypeConflictAndLeavesThatColumnAlone() {
        h.addNodeColumn("notes", Integer.class);

        Outcome outcome = succeed("result-update-tables", Ui.CURRENT_VIEW);

        assertEquals(Integer.class, h.nodeColumnType("notes"));
        assertNull(h.nodeValue("notes", 1));
        assertEquals(10, h.nodeValue("count", 1));
        assertTrue(outcome.message().contains("'notes'"), outcome.message());
    }

    @Test
    void addNetworksOpensEachAsANewCollectionWithAViewAndMakesTheFirstCurrent() {
        int before = h.collectionCount();

        succeed("result-add-networks", Ui.CURRENT_VIEW);

        assertEquals(before + 1, h.collectionCount());
        assertEquals("network name", h.currentNetworkName());
        assertEquals(2, h.currentNetworkNodeCount());
        assertEquals(1, h.currentNetworkEdgeCount());
        assertEquals(1, h.currentNetworkViewCount());
    }

    @Test
    void updateNetworkOnTheWholeNetworkUpdatesInPlace() {
        long suid = h.targetSuid();

        succeed("result-update-network", Ui.CURRENT_VIEW);

        assertEquals(suid, h.targetSuid());
        assertEquals(2, h.nodeCount());
        assertEquals(1, h.edgeCount());
        assertEquals("node 1", h.nodeValue("name", 0));
        assertEquals(5, h.nodeValue("age", 0));
        assertEquals(10, h.nodeValue("age", 1));
        assertFalse(h.hasNode(7));
        assertFalse(h.hasEdge(3));
        assertEquals("network name", h.networkValue("name"));
    }

    @Test
    void updateNetworkOnASelectionRemovesOnlySentElements() {
        h.select(0, 1, 7);

        succeed("result-update-network", Ui.CURRENT_VIEW);

        assertFalse(h.hasNode(7), "sent and absent from the result: removed");
        for (long id : List.of(2L, 15L, 25L, 100L)) {
            assertTrue(h.hasNode(id), "not sent: kept " + id);
        }
        assertTrue(h.hasEdge(3), "edges weren't selected, so none were sent");
        assertFalse(h.hasEdge(6), "edge 6 goes with its endpoint, node 7");
        assertEquals("node 1", h.nodeValue("name", 0));
    }

    @Test
    void addTablesChangesNothingAndWarns() {
        Map<String, Object> before = h.targetState();
        int collections = h.collectionCount();

        Outcome outcome = succeed("result-add-tables", Ui.CURRENT_VIEW);

        assertEquals(before, h.targetState());
        assertEquals(collections, h.collectionCount());
        assertTrue(outcome.message().contains("addTables"), outcome.message());
    }

    @Test
    void anUnknownActionIsReportedAndTheNextOneStillApplies() {
        Outcome outcome = succeed("result-unknown-action", Ui.CURRENT_VIEW);

        assertTrue(outcome.message().contains("explodeNetwork"), outcome.message());
        assertTrue(h.nodeSelected(15));
    }

    @Test
    void anUndeclaredActionIsStillApplied() {
        succeed("result-undeclared-action", Ui.CURRENT_VIEW);

        assertTrue(h.nodeSelected(15));
    }

    @Test
    void multipleActionsMatchApplyingEachAloneInOrder() {
        succeed("result-multi-action", Ui.CURRENT_VIEW);
        Map<String, Object> together = h.targetState();

        h.reset();
        otherBefore = h.otherState();
        for (String scenario :
                List.of(
                        "result-update-tables",
                        "result-update-layouts",
                        "result-update-selection")) {
            succeed(scenario, Ui.CURRENT_VIEW);
        }

        assertEquals(h.targetState(), together);
    }

    @Test
    void aNetworkActionWithNoNetworkOpenFailsTheRunNamingTheAction() {
        Outcome outcome = h.run("input-none", Ui.NO_NETWORK);

        assertFalse(outcome.succeeded());
        assertTrue(outcome.message().contains("updateLayouts"), outcome.message());
    }
}
