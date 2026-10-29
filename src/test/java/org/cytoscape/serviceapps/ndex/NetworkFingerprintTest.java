package org.cytoscape.serviceapps.ndex;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.serviceapps.request.IdMapper;
import org.cytoscape.serviceapps.testsupport.TestNetworks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class NetworkFingerprintTest {

    private final CyNetwork network = TestNetworks.withEveryColumnType();
    private final String before = NetworkFingerprint.of(network);

    private CyNode first() {
        return network.getNodeList().get(0);
    }

    @Test
    void anUnchangedNetworkKeepsItsFingerprint() {
        assertEquals(before, NetworkFingerprint.of(network));
    }

    @Test
    void addingOrRemovingANodeOrEdgeChangesIt() {
        CyNode added = network.addNode();
        assertNotEquals(before, NetworkFingerprint.of(network));

        network.removeNodes(List.of(added));
        assertEquals(before, NetworkFingerprint.of(network));

        network.addEdge(first(), first(), false);
        assertNotEquals(before, NetworkFingerprint.of(network));
    }

    @Test
    void changingANodeOrEdgeValueChangesIt() {
        network.getRow(first()).set("c_long", 7L);
        String nodeEdited = NetworkFingerprint.of(network);
        assertNotEquals(before, nodeEdited);

        network.getRow(network.getEdgeList().get(0)).set("e_double", 0.5);
        assertNotEquals(nodeEdited, NetworkFingerprint.of(network));
    }

    @Test
    void addingAColumnChangesIt() {
        network.getDefaultNodeTable().createColumn("cluster", String.class, false);
        assertNotEquals(before, NetworkFingerprint.of(network));
    }

    @Test
    void selectingAndDesktopBookkeepingLeaveItAlone() {
        network.getRow(first()).set(CyNetwork.SELECTED, true);
        IdMapper.of(network).ensureIds();
        network.getRow(first(), CyNetwork.HIDDEN_ATTRS)
                .getTable()
                .createColumn("app bookkeeping", String.class, false);

        assertEquals(before, NetworkFingerprint.of(network));
    }
}
