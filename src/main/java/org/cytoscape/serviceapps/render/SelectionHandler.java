package org.cytoscape.serviceapps.render;

import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.serviceapps.model.ResultAction;
import org.cytoscape.serviceapps.request.IdMapper;

/**
 * {@code updateSelection}: selects exactly the listed nodes and edges and clears every other
 * selection. Selection is stored on the network, so every view of it shows the change.
 *
 * @see <a
 *     href="https://github.com/cytoscape/cytoscape-web/wiki/updateSelection-Example">example</a>
 */
public class SelectionHandler implements ActionHandler {

    @Override
    public String action() {
        return ResultAction.UPDATE_SELECTION;
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    @Override
    public void apply(JsonElement data, UIInfo ui, RenderReport report) {
        JsonObject object = data.getAsJsonObject();
        CyNetwork network = ui.targetNetwork();
        IdMapper ids = IdMapper.of(network);
        ids.ensureIds();
        int unknown = 0;

        Set<CyNode> nodes = new HashSet<>();
        for (long id : longs(object.get("nodes"))) {
            CyNode node = ids.node(id);
            if (node != null && network.containsNode(node)) {
                nodes.add(node);
            } else {
                unknown++;
            }
        }
        Set<CyEdge> edges = new HashSet<>();
        for (long id : longs(object.get("edges"))) {
            CyEdge edge = ids.edge(id);
            if (edge != null && network.containsEdge(edge)) {
                edges.add(edge);
            } else {
                unknown++;
            }
        }

        for (CyNode node : network.getNodeList()) {
            select(network, node, nodes.contains(node));
        }
        for (CyEdge edge : network.getEdgeList()) {
            select(network, edge, edges.contains(edge));
        }
        if (unknown > 0) {
            report.note("updateSelection skipped " + unknown + " ID(s) not in the network");
        }
    }

    private static void select(CyNetwork network, CyIdentifiable element, boolean selected) {
        Boolean current = network.getRow(element).get(CyNetwork.SELECTED, Boolean.class);
        if (!Boolean.valueOf(selected).equals(current)) {
            network.getRow(element).set(CyNetwork.SELECTED, selected);
        }
    }

    private static long[] longs(JsonElement array) {
        if (array == null || !array.isJsonArray()) {
            return new long[0];
        }
        JsonArray values = array.getAsJsonArray();
        long[] result = new long[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i).getAsLong();
        }
        return result;
    }
}
