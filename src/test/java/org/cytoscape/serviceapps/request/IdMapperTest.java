package org.cytoscape.serviceapps.request;

import org.junit.jupiter.api.Test;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.serviceapps.testsupport.TestNetworks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** IDs round-trip for networks that did and didn't come from CX. */
class IdMapperTest {

    @Test
    void aNetworkThatNeverCameFromCxUsesSuids() {
        CyNetwork network = TestNetworks.withEveryColumnType();
        IdMapper ids = IdMapper.of(network);

        ids.ensureIds();

        for (CyNode node : network.getNodeList()) {
            assertEquals(node.getSUID().longValue(), ids.id(node));
            assertSame(rootOf(network).getNode(node.getSUID()), ids.node(node.getSUID()));
        }
        CyEdge edge = network.getEdgeList().get(0);
        assertEquals(edge.getSUID().longValue(), ids.id(edge));
        assertSame(rootOf(network).getEdge(edge.getSUID()), ids.edge(edge.getSUID()));
    }

    @Test
    void aNetworkFromCxKeepsItsIdsAndNewElementsGetTheNextOnes() {
        CyNetwork network = TestNetworks.empty();
        CyNode a = network.addNode();
        CyNode b = network.addNode();
        IdMapper ids = IdMapper.of(network);
        ids.assign(a, 7);
        ids.assign(b, 15);
        CyNode added = network.addNode();
        CyEdge edge = network.addEdge(a, b, true);

        ids.ensureIds();

        assertEquals(7, ids.id(a));
        assertEquals(15, ids.id(b));
        assertEquals(16, ids.id(added));
        assertEquals(edge.getSUID().longValue(), ids.id(edge));
        assertSame(a, ids.node(7));
        assertSame(added, ids.node(16));
    }

    @Test
    void idIsAssignedOnFirstUse() {
        CyNetwork network = TestNetworks.empty();
        CyNode node = network.addNode();

        assertEquals(node.getSUID().longValue(), IdMapper.of(network).id(node));
    }

    @Test
    void unknownIdsMapToNothing() {
        IdMapper ids = IdMapper.of(TestNetworks.withEveryColumnType());
        ids.ensureIds();

        assertNull(ids.node(-1));
        assertNull(ids.edge(-1));
    }

    @Test
    void theIdsLiveInTheCollectionsHiddenTable() {
        CyNetwork network = TestNetworks.withEveryColumnType();
        IdMapper.of(network).ensureIds();

        assertEquals(
                Long.class,
                rootOf(network)
                        .getTable(CyNode.class, CyNetwork.HIDDEN_ATTRS)
                        .getColumn(IdMapper.CX_ID_COLUMN)
                        .getType());
    }

    private static CyRootNetwork rootOf(CyNetwork network) {
        return ((CySubNetwork) network).getRootNetwork();
    }
}
