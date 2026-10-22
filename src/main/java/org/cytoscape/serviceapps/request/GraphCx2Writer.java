package org.cytoscape.serviceapps.request;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNode;

/**
 * Writes a minimal CX2 network with IDs only, for apps whose input is {@code model: graph}.
 *
 * @see <a
 *     href="https://github.com/cytoscape/cytoscape-web/wiki/serviceInputDefinition.inputNetwork">inputNetwork</a>
 */
final class GraphCx2Writer {

    private GraphCx2Writer() {}

    static JsonArray write(List<CyNode> nodes, List<CyEdge> edges, IdMapper ids) {
        JsonArray cx2 = new JsonArray();

        JsonObject version = new JsonObject();
        version.addProperty("CXVersion", "2.0");
        version.addProperty("hasFragments", false);
        cx2.add(version);

        JsonArray meta = new JsonArray();
        meta.add(metaData("nodes", nodes.size()));
        meta.add(metaData("edges", edges.size()));
        cx2.add(aspect("metaData", meta));

        JsonArray nodeArray = new JsonArray();
        for (CyNode node : nodes) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", ids.id(node));
            nodeArray.add(entry);
        }
        cx2.add(aspect("nodes", nodeArray));

        JsonArray edgeArray = new JsonArray();
        for (CyEdge edge : edges) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", ids.id(edge));
            entry.addProperty("s", ids.id(edge.getSource()));
            entry.addProperty("t", ids.id(edge.getTarget()));
            edgeArray.add(entry);
        }
        cx2.add(aspect("edges", edgeArray));

        JsonObject ok = new JsonObject();
        ok.addProperty("error", "");
        ok.addProperty("success", true);
        JsonArray status = new JsonArray();
        status.add(ok);
        cx2.add(aspect("status", status));
        return cx2;
    }

    private static JsonObject metaData(String name, int count) {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", name);
        entry.addProperty("elementCount", count);
        return entry;
    }

    private static JsonObject aspect(String name, JsonArray values) {
        JsonObject aspect = new JsonObject();
        aspect.add(name, values);
        return aspect;
    }
}
