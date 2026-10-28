package org.cytoscape.serviceapps.ndex;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.events.NetworkAboutToBeDestroyedEvent;
import org.cytoscape.model.events.NetworkAddedEvent;
import org.cytoscape.model.events.RowSetRecord;
import org.cytoscape.model.events.RowsSetEvent;
import org.cytoscape.serviceapps.testsupport.TestNetworks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkEditTrackerTest {

    private static final String UUID = "6f1b4ca6-0f2a-11ef-9a1f-005056ae23aa";

    private final NetworkEditTracker tracker =
            new NetworkEditTracker(TestNetworks.support().getNetworkTableManager());

    /** What Desktop delivers after CyNDEx-2 writes {@code column} on an import or a save. */
    private static RowsSetEvent cyNdex2Wrote(CyNetwork network, String column) {
        CyTable hidden = network.getTable(CyNetwork.class, CyNetwork.HIDDEN_ATTRS);
        CyRow row = hidden.getRow(network.getSUID());
        Object value = row.getRaw(column);
        return new RowsSetEvent(hidden, List.of(new RowSetRecord(row, column, value, value)));
    }

    private static void edit(CyNetwork network) {
        network.addNode();
    }

    @Test
    void aNetworkStartsUneditedAndAnEditMarksIt() {
        CyNetwork network = NdexLinkTest.withUuid(UUID);

        assertFalse(tracker.edited(network));
        edit(network);
        assertTrue(tracker.edited(network));
    }

    @Test
    void anImportOrSaveMakesTheCurrentContentTheNdexCopy() {
        CyNetwork network = NdexLinkTest.withUuid(UUID);
        tracker.markSaved(network);
        edit(network);
        assertTrue(tracker.edited(network));

        tracker.handleEvent(cyNdex2Wrote(network, NdexLink.MODIFICATION_COLUMN));
        assertFalse(tracker.edited(network));

        edit(network);
        tracker.handleEvent(cyNdex2Wrote(network, NdexLink.UUID_COLUMN));
        assertFalse(tracker.edited(network));
    }

    @Test
    void otherRowChangesLeaveTheNdexCopyAlone() {
        CyNetwork network = NdexLinkTest.withUuid(UUID);
        tracker.markSaved(network);
        edit(network);

        tracker.handleEvent(cyNdex2Wrote(network, CyNetwork.NAME));

        assertTrue(tracker.edited(network));
    }

    @Test
    void anAddedNetworkWithAUuidStartsClean() {
        CyNetwork network = NdexLinkTest.withUuid(UUID);
        tracker.handleEvent(
                new NetworkAddedEvent(TestNetworks.support().getNetworkManager(), network));
        edit(network);

        assertTrue(tracker.edited(network));
    }

    @Test
    void aDestroyedNetworkIsForgotten() {
        CyNetwork network = NdexLinkTest.withUuid(UUID);
        tracker.markSaved(network);
        edit(network);
        tracker.handleEvent(
                new NetworkAboutToBeDestroyedEvent(
                        TestNetworks.support().getNetworkManager(), network));

        assertFalse(tracker.edited(network), "asked afresh, it starts clean");
    }

    @Test
    void aNetworkWithNoUuidIsNeverMarkedSaved() {
        CyNetwork network = TestNetworks.withEveryColumnType();
        tracker.markSaved(network);
        edit(network);

        assertFalse(tracker.edited(network), "its first answer is its baseline");
    }
}
