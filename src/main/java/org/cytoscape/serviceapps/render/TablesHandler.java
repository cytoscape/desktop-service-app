package org.cytoscape.serviceapps.render;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
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
 *   <li>Rows are found through the request's element IDs; unknown IDs are skipped and counted.
 *   <li>A declared column that exists with a different type is reported and left unchanged.
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

        Map<String, ColumnType> accepted = new HashMap<>();
        Map<String, Boolean> rejected = new HashMap<>();
        if (object.has("columns")) {
            for (JsonElement entry : object.getAsJsonArray("columns")) {
                JsonObject column = entry.getAsJsonObject();
                String name = column.get("id").getAsString();
                String cx2 = column.has("type") ? column.get("type").getAsString() : "string";
                Optional<ColumnType> type = CyColumnTypes.fromCx2(cx2);
                if (type.isEmpty()) {
                    report.note(
                            "updateTables skipped column '"
                                    + name
                                    + "': unknown type '"
                                    + cx2
                                    + "'");
                    rejected.put(name, true);
                    continue;
                }
                Object defaultValue =
                        column.has("default")
                                ? CxValues.convert(column.get("default"), type.get())
                                : null;
                Optional<CyColumn> made = CxValues.column(table, name, type.get(), defaultValue);
                if (made.isEmpty()) {
                    report.problem(
                            "updateTables: column '"
                                    + name
                                    + "' is "
                                    + CxValues.describe(CxValues.typeOf(table.getColumn(name)))
                                    + " in Desktop but "
                                    + cx2
                                    + " in the result; left unchanged");
                    rejected.put(name, true);
                } else {
                    accepted.put(name, type.get());
                }
            }
        }

        IdMapper ids = IdMapper.of(network);
        ids.ensureIds();
        int unknown = 0;
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
                if (rejected.containsKey(name)) {
                    continue;
                }
                ColumnType type = accepted.get(name);
                if (type == null) {
                    // A value for a column the result didn't declare: use the existing column, or
                    // make one from the value's JSON type.
                    CyColumn existing = table.getColumn(name);
                    type =
                            existing != null
                                    ? CxValues.typeOf(existing)
                                    : CxValues.infer(cell.getValue());
                    if (CxValues.column(table, name, type, null).isEmpty()) {
                        continue;
                    }
                    accepted.put(name, type);
                }
                network.getRow(element).set(name, CxValues.convert(cell.getValue(), type));
            }
        }
        if (unknown > 0) {
            report.note("updateTables skipped " + unknown + " row(s) with IDs not in the network");
        }
    }

    private static CyIdentifiable element(
            IdMapper ids, CyNetwork network, boolean nodes, String key) {
        long id;
        try {
            id = Long.parseLong(key.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (nodes) {
            var node = ids.node(id);
            return node != null && network.containsNode(node) ? node : null;
        }
        var edge = ids.edge(id);
        return edge != null && network.containsEdge(edge) ? edge : null;
    }
}
