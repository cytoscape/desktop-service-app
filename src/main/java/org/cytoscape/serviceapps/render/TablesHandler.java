package org.cytoscape.serviceapps.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;
import org.cytoscape.serviceapps.model.ResultAction;
import org.cytoscape.serviceapps.request.IdMapper;
import org.cytoscape.serviceapps.types.CyColumnTypes;
import org.cytoscape.serviceapps.types.CyColumnTypes.ColumnType;

/**
 * {@code updateTables}: adds or updates columns of the node or edge table.
 *
 * <ul>
 *   <li>Only the {@code node} and {@code edge} tables are accepted.
 *   <li>Missing columns are created with the declared CX2 type and {@code default}.
 *   <li>Rows are found through the request's element IDs; unknown IDs are skipped and noted.
 *   <li>A declared column that exists with a different type, or an unknown type, fails the action
 *       before anything is written.
 * </ul>
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/updateTables-Example">example</a>
 */
public class TablesHandler implements ActionHandler {

    @Override
    public String action() {
        return ResultAction.UPDATE_TABLES;
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    @Override
    public void apply(JsonElement data, UIInfo ui, RenderReport report) {
        if (!data.isJsonObject()) {
            throw new IllegalArgumentException("updateTables data is not a table");
        }
        JsonObject object = data.getAsJsonObject();
        String id = object.has("id") ? object.get("id").getAsString() : "";
        CyNetwork network = ui.targetNetwork();
        boolean nodes;
        if ("node".equals(id)) {
            nodes = true;
        } else if ("edge".equals(id)) {
            nodes = false;
        } else {
            throw new IllegalArgumentException(
                    "updateTables can only update the node or edge table, not '" + id + "'");
        }
        CyTable table = nodes ? network.getDefaultNodeTable() : network.getDefaultEdgeTable();

        // Check everything before changing anything, so a failing action leaves the table as it
        // was.
        Map<String, ColumnType> types = new LinkedHashMap<>();
        Map<String, Object> defaults = new HashMap<>();
        if (object.has("columns")) {
            for (JsonElement entry : object.getAsJsonArray("columns")) {
                JsonObject column = entry.getAsJsonObject();
                String name = column.get("id").getAsString();
                String cx2 = column.has("type") ? column.get("type").getAsString() : "string";
                ColumnType type =
                        CyColumnTypes.fromCx2(cx2)
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "updateTables column '"
                                                                + name
                                                                + "' has unknown type '"
                                                                + cx2
                                                                + "'"));
                CxValues.requireType(table, name, type, "updateTables column");
                types.put(name, type);
                if (column.has("default")) {
                    defaults.put(name, CxValues.convert(column.get("default"), type));
                }
            }
        }

        // Element IDs live in a hidden column; filling in missing ones changes nothing the user
        // sees.
        IdMapper ids = IdMapper.of(network);
        ids.ensureIds();
        int unknown = 0;
        List<Write> writes = new ArrayList<>();
        JsonObject rows = object.has("rows") ? object.getAsJsonObject("rows") : new JsonObject();
        for (Map.Entry<String, JsonElement> row : rows.entrySet()) {
            CyIdentifiable element = element(ids, network, nodes, row.getKey());
            if (element == null) {
                unknown++;
                continue;
            }
            for (Map.Entry<String, JsonElement> cell :
                    row.getValue().getAsJsonObject().entrySet()) {
                String name = cell.getKey();
                ColumnType type = types.get(name);
                if (type == null) {
                    // A value for a column the result didn't declare: use the existing column, or
                    // make one from the value's JSON type.
                    CyColumn existing = table.getColumn(name);
                    type =
                            existing != null
                                    ? CxValues.typeOf(existing)
                                    : CxValues.infer(cell.getValue());
                    types.put(name, type);
                }
                writes.add(new Write(element, name, CxValues.convert(cell.getValue(), type)));
            }
        }

        for (Map.Entry<String, ColumnType> column : types.entrySet()) {
            CxValues.column(
                    table, column.getKey(), column.getValue(), defaults.get(column.getKey()));
        }
        for (Write write : writes) {
            network.getRow(write.element()).set(write.column(), write.value());
        }
        if (unknown > 0) {
            report.note("updateTables skipped " + unknown + " row(s) with IDs not in the network");
        }
    }

    private record Write(CyIdentifiable element, String column, Object value) {}

    private static CyIdentifiable element(
            IdMapper ids, CyNetwork network, boolean nodes, String key) {
        long id;
        try {
            id = Long.parseLong(key.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (nodes) {
            CyNode node = ids.node(id);
            return node != null && network.containsNode(node) ? node : null;
        }
        CyEdge edge = ids.edge(id);
        return edge != null && network.containsEdge(edge) ? edge : null;
    }
}
