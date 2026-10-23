package org.cytoscape.serviceapps.testsupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.serviceapps.cx.Cx2Support;
import org.cytoscape.serviceapps.request.IdMapper;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;

/**
 * Stands in for Cytoscape's CX Support app in JVM tests.
 *
 * <ul>
 *   <li>Export writes the nodes and edges with their {@code CX Element ID}s, as CX Support does,
 *       and records each call.
 *   <li>Import builds real networks (nodes, edges, string/number attributes) in a new collection,
 *       registers them and a view.
 * </ul>
 *
 * The real CX Support code is exercised by the Docker integration tests.
 */
public class FakeCx2Support implements Cx2Support {

    /** One export call. */
    public record Export(CyNetwork network, CyNetworkView view, int nodes, int edges) {}

    public final List<Export> exports = new ArrayList<>();
    private final List<Runnable> availabilityListeners = new ArrayList<>();
    private final CyNetworkManager networkManager;
    private final CyNetworkViewManager viewManager;
    private final CyNetworkViewFactory viewFactory;
    private boolean available = true;

    public FakeCx2Support(
            CyNetworkManager networkManager,
            CyNetworkViewManager viewManager,
            CyNetworkViewFactory viewFactory) {
        this.networkManager = networkManager;
        this.viewManager = viewManager;
        this.viewFactory = viewFactory;
    }

    public void setAvailable(boolean available) {
        this.available = available;
        availabilityListeners.forEach(Runnable::run);
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public void addAvailabilityListener(Runnable listener) {
        availabilityListeners.add(listener);
    }

    @Override
    public JsonArray export(CyNetwork network, CyNetworkView view) {
        exports.add(new Export(network, view, network.getNodeCount(), network.getEdgeCount()));
        IdMapper ids = IdMapper.of(network);
        JsonArray nodes = new JsonArray();
        for (CyNode node : network.getNodeList()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", ids.id(node));
            nodes.add(entry);
        }
        JsonArray edges = new JsonArray();
        for (CyEdge edge : network.getEdgeList()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", ids.id(edge));
            entry.addProperty("s", ids.id(edge.getSource()));
            entry.addProperty("t", ids.id(edge.getTarget()));
            edges.add(entry);
        }
        JsonArray cx2 = new JsonArray();
        JsonObject version = new JsonObject();
        version.addProperty("CXVersion", "2.0");
        cx2.add(version);
        JsonObject nodeAspect = new JsonObject();
        nodeAspect.add("nodes", nodes);
        cx2.add(nodeAspect);
        JsonObject edgeAspect = new JsonObject();
        edgeAspect.add("edges", edges);
        cx2.add(edgeAspect);
        return cx2;
    }

    @Override
    public List<CyNetwork> importAndRegister(JsonArray cx2, String name) {
        CyNetwork network = TestNetworks.support().getNetworkFactory().createNetwork();
        IdMapper ids = IdMapper.of(network);
        Map<Long, CyNode> nodes = new HashMap<>();
        for (JsonElement aspect : cx2) {
            JsonObject object = aspect.getAsJsonObject();
            if (object.has("networkAttributes")) {
                for (JsonElement attrs : object.getAsJsonArray("networkAttributes")) {
                    attrs.getAsJsonObject()
                            .entrySet()
                            .forEach(e -> setString(network, network, e.getKey(), e.getValue()));
                }
            }
            if (object.has("nodes")) {
                for (JsonElement n : object.getAsJsonArray("nodes")) {
                    JsonObject node = n.getAsJsonObject();
                    CyNode created = network.addNode();
                    long id = node.get("id").getAsLong();
                    ids.assign(created, id);
                    nodes.put(id, created);
                    if (node.has("v")) {
                        node.getAsJsonObject("v")
                                .entrySet()
                                .forEach(
                                        e -> setString(network, created, e.getKey(), e.getValue()));
                    }
                }
            }
            if (object.has("edges")) {
                for (JsonElement e : object.getAsJsonArray("edges")) {
                    JsonObject edge = e.getAsJsonObject();
                    CyEdge created =
                            network.addEdge(
                                    nodes.get(edge.get("s").getAsLong()),
                                    nodes.get(edge.get("t").getAsLong()),
                                    true);
                    ids.assign(created, edge.get("id").getAsLong());
                }
            }
        }
        networkManager.addNetwork(network);
        viewManager.addNetworkView(viewFactory.createNetworkView(network));
        return List.of(network);
    }

    private static void setString(
            CyNetwork network, CyIdentifiable element, String column, JsonElement value) {
        var table = network.getRow(element).getTable();
        if (table.getColumn(column) == null) {
            table.createColumn(column, String.class, false);
        }
        if (table.getColumn(column).getType() == String.class) {
            network.getRow(element).set(column, value.getAsString());
        }
    }
}
