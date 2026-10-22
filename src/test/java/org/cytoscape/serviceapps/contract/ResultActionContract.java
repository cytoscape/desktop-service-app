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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The result action rendering contract (#44 test spec B), written once against {@link
 * ContractHarness}. Each scenario installs a fixture app whose result is the spec's example for one
 * action, runs it, and checks Desktop's state. The second network must never change.
 *
 * <p>Failures are fail-fast, and their messages are asserted exactly. Every entry path runs these
 * same assertions, so the menu path and {@code task call} report identical messages.
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

    private Outcome fail(String scenario, Ui ui) {
        Outcome outcome = h.run(scenario, ui);
        assertFalse(outcome.succeeded(), scenario + " should fail: " + outcome.message());
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
        assumeTrue(h.supportsViewTarget(), "this harness always targets every view");
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
    void updateTablesCreatesColumnsWritesRowsByIdAndNotesUnknownIds() {
        Outcome outcome = succeed("result-update-tables", Ui.CURRENT_VIEW);

        assertEquals(Integer.class, h.nodeColumnType("count"));
        assertEquals(10, h.nodeValue("count", 1));
        assertEquals("lowest", h.nodeValue("notes", 1));
        assertEquals(24, h.nodeValue("count", 2));
        assertNull(h.nodeValue("count", 7));
        assertEquals(
                "Applied updateTables. Notes: updateTables skipped 1 row(s) with IDs not in the"
                        + " network",
                outcome.message());
    }

    @Test
    void updateTablesWithATypeConflictFailsBeforeWritingAnything() {
        h.addNodeColumn("notes", Integer.class);
        Map<String, Object> before = h.targetState();

        Outcome outcome = fail("result-update-tables", Ui.CURRENT_VIEW);

        assertEquals(
                "Rendering failed at updateTables (1 of 1): updateTables column 'notes' is integer"
                        + " in Desktop but string in the result. Nothing was applied.",
                outcome.message());
        assertEquals(before, h.targetState());
        assertNull(h.nodeColumnType("count"), "no column of the failing action is written");
    }

    @Test
    void updateTablesWithAnUnknownColumnTypeFails() {
        Map<String, Object> before = h.targetState();

        Outcome outcome = fail("result-unknown-column-type", Ui.CURRENT_VIEW);

        assertEquals(
                "Rendering failed at updateTables (1 of 1): updateTables column 'shape' has unknown"
                        + " type 'complex'. Nothing was applied.",
                outcome.message());
        assertEquals(before, h.targetState());
    }

    @Test
    void aFailingActionStopsTheRunAndLaterActionsAreNotApplied() {
        Outcome outcome = fail("result-fail-fast", Ui.CURRENT_VIEW);

        assertEquals(
                "Rendering failed at updateTables (2 of 3): updateTables can only update the node or"
                        + " edge table, not 'network'. Applied: updateLayouts. Not applied:"
                        + " updateSelection.",
                outcome.message());
        assertArrayEquals(new double[] {25.2, -473.6}, xy(0, 7), "updateLayouts stays applied");
        assertFalse(h.nodeSelected(15), "updateSelection was not applied");
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
    void updateNetworkOnTheWholeNetworkUpdatesInPlaceAndNotesWhatItRemoved() {
        long suid = h.targetSuid();

        Outcome outcome = succeed("result-update-network", Ui.CURRENT_VIEW);

        assertEquals(
                "Applied updateNetwork. Notes: updateNetwork removed 5 node(s) and 2 edge(s)",
                outcome.message());

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

        Outcome outcome = succeed("result-update-network", Ui.CURRENT_VIEW);

        assertFalse(h.hasNode(7), "sent and absent from the result: removed");
        for (long id : List.of(2L, 15L, 25L, 100L)) {
            assertTrue(h.hasNode(id), "not sent: kept " + id);
        }
        assertTrue(h.hasEdge(3), "edges weren't selected, so none were sent");
        assertFalse(h.hasEdge(6), "edge 6 goes with its endpoint, node 7");
        assertEquals("node 1", h.nodeValue("name", 0));
        assertEquals(
                "Applied updateNetwork. Notes: updateNetwork removed 1 node(s) and 0 edge(s)",
                outcome.message());
    }

    @Test
    void updateNetworkWithATypeConflictFailsWithTheNetworkUnchanged() {
        h.addNodeColumn("age", String.class);
        long suid = h.targetSuid();
        Map<String, Object> before = h.targetState();

        Outcome outcome = fail("result-update-network", Ui.CURRENT_VIEW);

        assertEquals(
                "Rendering failed at updateNetwork (1 of 1): updateNetwork attribute 'age' is string"
                        + " in Desktop but integer in the result. Nothing was applied.",
                outcome.message());
        assertEquals(suid, h.targetSuid());
        assertEquals(before, h.targetState());
    }

    @Test
    void updateNetworkWithInvalidCx2FailsWithTheNetworkUnchanged() {
        Map<String, Object> before = h.targetState();

        Outcome outcome = fail("result-invalid-cx2", Ui.CURRENT_VIEW);

        assertEquals(
                "Rendering failed at updateNetwork (1 of 1): updateNetwork edge 9 connects nodes that"
                        + " aren't in the result. Nothing was applied.",
                outcome.message());
        assertEquals(before, h.targetState());
    }

    @Test
    void updateNetworkOnAnHcxNetworkIsSkippedWithANote() {
        h.setNetworkAttribute("ndexSchema", "hierarchy_v0.1");
        Map<String, Object> before = h.targetState();

        Outcome outcome = succeed("result-update-network", Ui.CURRENT_VIEW);

        assertEquals(
                "No result actions applied. Notes: updateNetwork skipped: HCX networks can't be"
                        + " updated in place yet",
                outcome.message());
        assertEquals(before, h.targetState());
    }

    @Test
    void malformedActionDataFailsWithNothingChanged() {
        Map<String, Object> before = h.targetState();

        Outcome outcome = fail("result-malformed-layouts", Ui.CURRENT_VIEW);

        assertEquals(
                "Rendering failed at updateLayouts (1 of 1): updateLayouts data is not a list of node"
                        + " positions. Nothing was applied.",
                outcome.message());
        assertEquals(before, h.targetState());
    }

    @Test
    void openUrlWithATargetFailsAndOpensNothing() {
        Outcome outcome = fail("result-open-url-iframe", Ui.CURRENT_VIEW);

        assertEquals(
                "Rendering failed at openURL (1 of 1): openURL target 'frame' isn't supported in"
                        + " Cytoscape Desktop; only a null or absent target opens the web browser."
                        + " Nothing was applied.",
                outcome.message());
        assertEquals(List.of(), h.openedUrls());
    }

    @Test
    void aBrowserThatCantOpenFailsTheRun() {
        assumeTrue(h.canFailBrowser(), "this harness can't make the browser fail");
        h.failBrowser();

        Outcome outcome = fail("result-open-url", Ui.CURRENT_VIEW);

        assertEquals(
                "Rendering failed at openURL (1 of 1): no web browser could be opened for"
                        + " https://www.cytoscape.org. Nothing was applied.",
                outcome.message());
    }

    @Test
    void addTablesIsSkippedWithANoteAndChangesNothing() {
        Map<String, Object> before = h.targetState();
        int collections = h.collectionCount();

        Outcome outcome = succeed("result-add-tables", Ui.CURRENT_VIEW);

        assertEquals(before, h.targetState());
        assertEquals(collections, h.collectionCount());
        assertEquals(
                "No result actions applied. Notes: addTables is not supported yet and was skipped",
                outcome.message());
    }

    @Test
    void anUnknownActionFailsBeforeAnythingIsApplied() {
        Outcome outcome = fail("result-unknown-action", Ui.CURRENT_VIEW);

        assertEquals(
                "Rendering failed: the result asks for unknown action 'explodeNetwork'. Nothing was"
                        + " applied.",
                outcome.message());
        assertFalse(h.nodeSelected(15), "the following updateSelection was not applied");
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
    void aNetworkActionWithNoNetworkFailsTheRunNamingTheAction() {
        Outcome outcome = fail("input-none", Ui.NO_NETWORK);

        assertEquals(
                "Rendering failed: the result asks to updateLayouts, which needs a network, and no"
                        + " network was targeted. Nothing was applied.",
                outcome.message());
    }

    @Test
    void aFailedServiceTaskFailsTheRunWithTheServiceMessage() {
        Map<String, Object> before = h.targetState();

        Outcome outcome = fail("failure-failed", Ui.CURRENT_VIEW);

        assertEquals("Service task failed: Algorithm failed: bad input", outcome.message());
        assertEquals(before, h.targetState());
    }

    @Test
    void aRefusedTaskFailsTheRun() {
        Outcome outcome = fail("failure-submit-500", Ui.CURRENT_VIEW);

        assertTrue(
                outcome.message()
                        .startsWith(
                                "Service task failed: Fixture failure-submit-500 didn't accept the"
                                        + " task: "),
                outcome.message());
    }
}
