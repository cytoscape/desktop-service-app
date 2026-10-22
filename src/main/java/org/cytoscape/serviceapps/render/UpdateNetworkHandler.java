package org.cytoscape.serviceapps.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.serviceapps.model.ResultAction;
import org.cytoscape.serviceapps.request.IdMapper;
import org.cytoscape.serviceapps.types.CyColumnTypes.ColumnType;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.model.View;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;

/**
 * {@code updateNetwork}: updates the target network in place from the returned CX2, keeping its
 * identity (SUID, views, style).
 *
 * <ul>
 *   <li>The CX2, and every value's type against the existing columns, are checked before anything
 *       changes; a problem fails the action with the network unchanged.
 *   <li>Nodes and edges are matched by element ID; matches get their attributes updated, and new
 *       ones are added.
 *   <li>Only elements the request sent and the result left out are removed, so a run on a selection
 *       never deletes the rest of the network.
 *   <li>Positions go to every view of the network. Network attributes are set; the network keeps
 *       its name when the result has none.
 *   <li>Not in this version: the visual style and opaque aspects stay unchanged. HCX networks are
 *       skipped with a note, as Cytoscape Web does.
 * </ul>
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/updateNetwork-Example">example</a>
 */
public class UpdateNetworkHandler implements ActionHandler {

    private final CyNetworkViewManager viewManager;

    public UpdateNetworkHandler(CyNetworkViewManager viewManager) {
        this.viewManager = viewManager;
    }

    @Override
    public String action() {
        return ResultAction.UPDATE_NETWORK;
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    @Override
    public void apply(JsonElement data, UIInfo ui, RenderReport report) {
        CyNetwork network = ui.targetNetwork();
        if (isHcx(network)) {
            // Cytoscape Web warns and carries on with the other actions.
            report.skip("updateNetwork skipped: HCX networks can't be updated in place yet");
            return;
        }
        Cx2Document document = Cx2Document.parse(data);
        checkNoConflicts(network, document);
        IdMapper ids = IdMapper.of(network);
        ids.ensureIds();

        Map<Long, CyNode> nodes = new HashMap<>();
        for (Cx2Document.Node node : document.nodes) {
            CyNode element = ids.node(node.id());
            if (element == null) {
                element = network.addNode();
                ids.assign(element, node.id());
            } else if (!network.containsNode(element)) {
                ((CySubNetwork) network).addNode(element);
            }
            nodes.put(node.id(), element);
            setValues(network, element, document, "nodes", node.values(), report);
        }

        Set<Long> keptEdges = new HashSet<>();
        for (Cx2Document.Edge edge : document.edges) {
            keptEdges.add(edge.id());
            CyEdge element = ids.edge(edge.id());
            if (element == null) {
                element = network.addEdge(nodes.get(edge.source()), nodes.get(edge.target()), true);
                ids.assign(element, edge.id());
            } else if (!network.containsEdge(element)) {
                ((CySubNetwork) network).addEdge(element);
            }
            setValues(network, element, document, "edges", edge.values(), report);
        }

        List<CyEdge> edgesToRemove = new ArrayList<>();
        for (long id : ui.sent().edges()) {
            CyEdge edge = ids.edge(id);
            if (!keptEdges.contains(id) && edge != null && network.containsEdge(edge)) {
                edgesToRemove.add(edge);
            }
        }
        network.removeEdges(edgesToRemove);
        List<CyNode> nodesToRemove = new ArrayList<>();
        for (long id : ui.sent().nodes()) {
            CyNode node = ids.node(id);
            if (!nodes.containsKey(id) && node != null && network.containsNode(node)) {
                nodesToRemove.add(node);
            }
        }
        network.removeNodes(nodesToRemove);

        setNetworkAttributes(network, document, report);
        applyPositions(network, document, nodes);
        if (!nodesToRemove.isEmpty() || !edgesToRemove.isEmpty()) {
            report.note(
                    "updateNetwork removed "
                            + nodesToRemove.size()
                            + " node(s) and "
                            + edgesToRemove.size()
                            + " edge(s)");
        }
    }

    /**
     * Fails before anything changes when a value's type differs from an existing column's, and
     * converts every value once so a bad value fails here too.
     */
    private static void checkNoConflicts(CyNetwork network, Cx2Document document) {
        for (Cx2Document.Node node : document.nodes) {
            check(network.getDefaultNodeTable(), document, "nodes", node.values());
        }
        for (Cx2Document.Edge edge : document.edges) {
            check(network.getDefaultEdgeTable(), document, "edges", edge.values());
        }
        check(
                network.getDefaultNetworkTable(),
                document,
                "networkAttributes",
                document.networkAttributes);
    }

    private static void check(
            CyTable table, Cx2Document document, String kind, Map<String, JsonElement> values) {
        for (Map.Entry<String, JsonElement> value : values.entrySet()) {
            Cx2Document.Attribute declared = document.declared(kind, value.getKey());
            ColumnType type = declared != null ? declared.type() : CxValues.infer(value.getValue());
            CyColumn existing = table.getColumn(value.getKey());
            if (existing != null && !CxValues.typeOf(existing).equals(type)) {
                throw new IllegalArgumentException(
                        "updateNetwork attribute '"
                                + value.getKey()
                                + "' is "
                                + CxValues.describe(CxValues.typeOf(existing))
                                + " in Desktop but "
                                + CxValues.describe(type)
                                + " in the result");
            }
            CxValues.convert(value.getValue(), type);
        }
    }

    /** HCX: an {@code ndexSchema} starting with "hierarchy", or any {@code HCX::} attribute. */
    static boolean isHcx(CyNetwork network) {
        CyTable table = network.getDefaultNetworkTable();
        for (CyColumn column : table.getColumns()) {
            if (column.getName().startsWith("HCX::")) {
                return true;
            }
        }
        if (table.getColumn("ndexSchema") != null) {
            Object schema = network.getRow(network).getRaw("ndexSchema");
            return schema != null && schema.toString().startsWith("hierarchy");
        }
        return false;
    }

    private static void setValues(
            CyNetwork network,
            CyIdentifiable element,
            Cx2Document document,
            String kind,
            Map<String, JsonElement> values,
            RenderReport report) {
        CyRow row = network.getRow(element);
        CyTable table = row.getTable();
        for (Map.Entry<String, JsonElement> value : values.entrySet()) {
            Cx2Document.Attribute declared = document.declared(kind, value.getKey());
            ColumnType type = declared != null ? declared.type() : CxValues.infer(value.getValue());
            CxValues.column(table, value.getKey(), type, null);
            row.set(value.getKey(), CxValues.convert(value.getValue(), type));
        }
    }

    private static void setNetworkAttributes(
            CyNetwork network, Cx2Document document, RenderReport report) {
        CyRow row = network.getRow(network);
        CyTable table = row.getTable();
        for (Map.Entry<String, JsonElement> attribute : document.networkAttributes.entrySet()) {
            Cx2Document.Attribute declared =
                    document.declared("networkAttributes", attribute.getKey());
            ColumnType type =
                    declared != null ? declared.type() : CxValues.infer(attribute.getValue());
            CxValues.column(table, attribute.getKey(), type, null);
            row.set(attribute.getKey(), CxValues.convert(attribute.getValue(), type));
        }
    }

    private void applyPositions(CyNetwork network, Cx2Document document, Map<Long, CyNode> nodes) {
        List<CyNetworkView> views = new ArrayList<>(viewManager.getNetworkViews(network));
        if (views.isEmpty()) {
            return;
        }
        Edt.run(
                () -> {
                    for (Cx2Document.Node node : document.nodes) {
                        if (node.x() == null || node.y() == null) {
                            continue;
                        }
                        for (CyNetworkView view : views) {
                            View<CyNode> nodeView = view.getNodeView(nodes.get(node.id()));
                            if (nodeView == null) {
                                continue;
                            }
                            nodeView.setVisualProperty(
                                    BasicVisualLexicon.NODE_X_LOCATION, node.x());
                            nodeView.setVisualProperty(
                                    BasicVisualLexicon.NODE_Y_LOCATION, node.y());
                            if (node.z() != null) {
                                nodeView.setVisualProperty(
                                        BasicVisualLexicon.NODE_Z_LOCATION, node.z());
                            }
                        }
                    }
                    views.forEach(CyNetworkView::updateView);
                });
    }
}
