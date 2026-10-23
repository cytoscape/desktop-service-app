package org.cytoscape.serviceapps.testsupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.serviceapps.request.IdMapper;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.network.NetworkViewFactoryTestSupport;

/**
 * A network whose element IDs match the fixture's result scenarios: nodes 0, 1, 2, 7, 15, 25 and
 * 100; edges 3 (0 to 1) and 6 (7 to 15). It has one real view.
 */
public final class ContractNetwork {

    public static final List<Long> NODE_IDS = List.of(0L, 1L, 2L, 7L, 15L, 25L, 100L);
    public static final long EDGE_3 = 3;
    public static final long EDGE_6 = 6;

    private static final NetworkViewFactoryTestSupport VIEWS = new NetworkViewFactoryTestSupport();

    public final CyNetwork network;
    public final CyNetworkView view;
    private final IdMapper ids;
    private final Map<Long, CyNode> nodes = new HashMap<>();
    private final Map<Long, CyEdge> edges = new HashMap<>();

    public ContractNetwork(String name) {
        network = TestNetworks.support().getNetwork();
        network.getRow(network).set(CyNetwork.NAME, name);
        ids = IdMapper.of(network);
        for (long id : NODE_IDS) {
            CyNode node = network.addNode();
            ids.assign(node, id);
            network.getRow(node).set(CyNetwork.NAME, "n" + id);
            nodes.put(id, node);
        }
        edges.put(EDGE_3, edge(0, 1, EDGE_3));
        edges.put(EDGE_6, edge(7, 15, EDGE_6));
        view = VIEWS.getNetworkViewFactory().createNetworkView(network);
    }

    private CyEdge edge(long source, long target, long id) {
        CyEdge edge = network.addEdge(nodes.get(source), nodes.get(target), true);
        ids.assign(edge, id);
        return edge;
    }

    public CyNode node(long id) {
        return nodes.get(id);
    }

    public CyEdge edgeById(long id) {
        return edges.get(id);
    }

    /** Creates another view of the same network. */
    public CyNetworkView newView() {
        return VIEWS.getNetworkViewFactory().createNetworkView(network);
    }

    /** A copy of every node and edge row value, for "nothing changed" checks. */
    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("nodes", network.getNodeCount());
        snapshot.put("edges", network.getEdgeCount());
        List<Object> rows = new ArrayList<>();
        network.getDefaultNodeTable().getAllRows().forEach(row -> rows.add(row.getAllValues()));
        network.getDefaultEdgeTable().getAllRows().forEach(row -> rows.add(row.getAllValues()));
        snapshot.put("rows", rows.toString());
        snapshot.put("network", network.getRow(network).getAllValues().toString());
        return snapshot;
    }

    public static NetworkViewFactoryTestSupport views() {
        return VIEWS;
    }
}
