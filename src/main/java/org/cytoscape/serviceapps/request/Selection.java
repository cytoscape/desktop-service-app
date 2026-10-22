package org.cytoscape.serviceapps.request;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTableUtil;
import org.cytoscape.serviceapps.app.AppSupport;

/**
 * The nodes and edges a run sends, picked by the app's {@code scope}.
 *
 * <ul>
 *   <li>{@code dynamic} (the default): the selection if there is one, otherwise everything.
 *   <li>{@code selected}: only the selection.
 *   <li>{@code all}: everything.
 * </ul>
 *
 * Nodes and edges are filtered separately, as in Cytoscape Web.
 *
 * @param whole whether everything is sent, rather than a selection
 */
public record Selection(List<CyNode> nodes, List<CyEdge> edges, boolean whole) {

    /** Picks the elements for an app with this input type and scope. */
    public static Selection of(CyNetwork network, String type, String scope) {
        List<CyNode> selectedNodes = CyTableUtil.getSelectedNodes(network);
        List<CyEdge> selectedEdges = CyTableUtil.getSelectedEdges(network);
        boolean all = AppSupport.SCOPE_ALL.equals(scope);
        boolean selected = AppSupport.SCOPE_SELECTED.equals(scope);

        switch (type) {
            case AppSupport.INPUT_NODE:
                return pick(all, selected, !selectedNodes.isEmpty())
                        ? new Selection(selectedNodes, List.of(), false)
                        : new Selection(network.getNodeList(), List.of(), true);
            case AppSupport.INPUT_EDGE:
                return pick(all, selected, !selectedEdges.isEmpty())
                        ? new Selection(List.of(), selectedEdges, false)
                        : new Selection(List.of(), network.getEdgeList(), true);
            default:
                return pick(all, selected, !selectedNodes.isEmpty() || !selectedEdges.isEmpty())
                        ? new Selection(selectedNodes, selectedEdges, false)
                        : new Selection(network.getNodeList(), network.getEdgeList(), true);
        }
    }

    private static boolean pick(boolean all, boolean selected, boolean hasSelection) {
        return !all && (selected || hasSelection);
    }

    /** The selected nodes plus the endpoints of the selected edges, so the network is valid. */
    public List<CyNode> nodesWithEdgeEndpoints() {
        Set<CyNode> nodes = new LinkedHashSet<>(this.nodes);
        for (CyEdge edge : edges) {
            nodes.add(edge.getSource());
            nodes.add(edge.getTarget());
        }
        return new ArrayList<>(nodes);
    }
}
