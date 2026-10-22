package org.cytoscape.serviceapps.request;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.serviceapps.types.CyColumnTypes;

/**
 * Writes node or edge columns in the shape the spec gives for table input: {@code {"columns":
 * [{"id": name, "type": cx2Type}], "rows": {"<id>": {name: value}}}}.
 *
 * @see <a
 *     href="https://github.com/cytoscape/cytoscape-web/wiki/serviceInputDefinition.inputColumns">inputColumns</a>
 */
final class TableDataBuilder {

    private TableDataBuilder() {}

    /**
     * @param columns the column names to send, in order
     * @throws RequestException if a column doesn't exist in the table
     */
    static JsonObject build(
            CyNetwork network,
            boolean nodes,
            List<? extends CyIdentifiable> elements,
            List<String> columns,
            IdMapper ids) {
        CyTable table = nodes ? network.getDefaultNodeTable() : network.getDefaultEdgeTable();
        JsonArray declared = new JsonArray();
        for (String name : columns) {
            CyColumn column = table.getColumn(name);
            if (column == null) {
                throw new RequestException(
                        "The current network has no "
                                + (nodes ? "node" : "edge")
                                + " column named '"
                                + name
                                + "', which this service app needs.");
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("id", name);
            entry.addProperty("type", CyColumnTypes.toCx2(column).orElse("string"));
            declared.add(entry);
        }

        JsonObject rows = new JsonObject();
        for (CyIdentifiable element : elements) {
            CyRow row = network.getRow(element);
            JsonObject values = new JsonObject();
            for (String name : columns) {
                Object value = row.getRaw(name);
                if (value != null) {
                    values.add(name, toJson(value));
                }
            }
            rows.add(Long.toString(ids.id(element)), values);
        }

        JsonObject data = new JsonObject();
        data.add("columns", declared);
        data.add("rows", rows);
        return data;
    }

    static JsonElement toJson(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof List<?> list) {
            JsonArray array = new JsonArray();
            for (Object item : list) {
                array.add(toJson(item));
            }
            return array;
        }
        if (value instanceof Number number) {
            return new JsonPrimitive(number);
        }
        if (value instanceof Boolean flag) {
            return new JsonPrimitive(flag);
        }
        return new JsonPrimitive(value.toString());
    }
}
