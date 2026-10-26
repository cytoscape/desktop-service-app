package org.cytoscape.serviceapps.request;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.serviceapps.app.AppSupport;
import org.cytoscape.serviceapps.cx.Cx2Support;
import org.cytoscape.serviceapps.model.ServiceInputDefinition;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.model.TaskRequest;
import org.cytoscape.view.model.CyNetworkView;

/**
 * Turns a run's input and its fixed target network into the spec's request body: {@code
 * {"algorithm": name, "data": ..., "parameters": {...}}}, with parameter values as text.
 *
 * <ul>
 *   <li>No input type, or {@code none}: {@code data} is {@code {}}.
 *   <li>Node or edge input: the chosen columns as a table, rows keyed by element ID.
 *   <li>Network input, model {@code network}: CX2 from CX Support. A selection is written from a
 *       temporary, unregistered subnetwork with the selected edges' endpoints, removed afterwards;
 *       positions aren't sent for selections.
 *   <li>Network input, model {@code graph}: minimal CX2 with IDs only.
 * </ul>
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/Task-Request">Task Request</a>
 */
public class RequestBuilder {

    private final Cx2Support cx2;

    public RequestBuilder(Cx2Support cx2) {
        this.cx2 = cx2;
    }

    /**
     * @param target the network the run works on; null only for apps with no input type
     * @param view the target's view, for positions and style; may be null
     * @throws RequestException when the network can't supply what the app needs
     */
    public BuiltRequest build(
            ServiceMetadata meta, ServiceInput input, CyNetwork target, CyNetworkView view) {
        String type = AppSupport.inputType(meta);
        JsonElement data;
        SentIds sent;

        if (type == null) {
            data = new JsonObject();
            sent = SentIds.NONE;
        } else {
            if (target == null) {
                throw new RequestException("Open a network first; this service app works on it.");
            }
            IdMapper ids = IdMapper.of(target);
            ids.ensureIds();
            Selection selection = Selection.of(target, type, AppSupport.scope(meta));
            if (AppSupport.INPUT_NETWORK.equals(type)) {
                ServiceInputDefinition.InputNetwork network = AppSupport.inputNetwork(meta);
                boolean graphOnly = network != null && "graph".equalsIgnoreCase(network.model);
                List<CyNode> nodes =
                        selection.whole() ? selection.nodes() : selection.nodesWithEdgeEndpoints();
                data =
                        graphOnly
                                ? GraphCx2Writer.write(nodes, selection.edges(), ids)
                                : networkCx2(target, view, selection, nodes);
                sent = sentIds(nodes, selection.edges(), ids);
            } else {
                boolean nodes = AppSupport.INPUT_NODE.equals(type);
                data =
                        TableDataBuilder.build(
                                target,
                                nodes,
                                nodes ? selection.nodes() : selection.edges(),
                                columns(meta, input),
                                ids);
                sent =
                        nodes
                                ? sentIds(selection.nodes(), List.of(), ids)
                                : sentIds(List.of(), selection.edges(), ids);
            }
        }

        Map<String, String> parameters = new LinkedHashMap<>();
        input.parameters().forEach((key, value) -> parameters.put(key, value == null ? "" : value));
        return new BuiltRequest(new TaskRequest(meta.name, data, parameters), sent);
    }

    private JsonArray networkCx2(
            CyNetwork target, CyNetworkView view, Selection selection, List<CyNode> nodes) {
        if (selection.whole()) {
            return cx2.export(target, view);
        }
        CyRootNetwork root = ((CySubNetwork) target).getRootNetwork();
        CySubNetwork subset = root.addSubNetwork(nodes, selection.edges());
        try {
            return cx2.export(subset, null);
        } finally {
            root.removeSubNetwork(subset);
        }
    }

    /** The user's chosen columns, then the columns the app always receives. */
    private static List<String> columns(ServiceMetadata meta, ServiceInput input) {
        List<String> columns = new ArrayList<>();
        ServiceInputDefinition definition = meta.serviceInputDefinition;
        for (ServiceInputDefinition.InputColumn column : definition.inputColumns()) {
            List<String> chosen;
            if (column.columnName != null && !column.columnName.isBlank()) {
                chosen = List.of(column.columnName);
            } else {
                chosen = input.inputColumns().getOrDefault(column.name, List.of());
            }
            for (String name : chosen) {
                if (!columns.contains(name)) {
                    columns.add(name);
                }
            }
        }
        return columns;
    }

    private static SentIds sentIds(List<CyNode> nodes, List<CyEdge> edges, IdMapper ids) {
        Set<Long> nodeIds = new HashSet<>();
        for (CyNode node : nodes) {
            nodeIds.add(ids.id(node));
        }
        Set<Long> edgeIds = new HashSet<>();
        for (CyEdge edge : edges) {
            edgeIds.add(ids.id(edge));
        }
        return new SentIds(nodeIds, edgeIds);
    }
}
