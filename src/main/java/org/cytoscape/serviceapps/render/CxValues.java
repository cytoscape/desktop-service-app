package org.cytoscape.serviceapps.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyTable;
import org.cytoscape.serviceapps.types.CyColumnTypes;
import org.cytoscape.serviceapps.types.CyColumnTypes.ColumnType;

/** Converts CX2 JSON values to Desktop column values, and makes columns for them. */
final class CxValues {

    private CxValues() {}

    /** The column type for a JSON value with no declared type. */
    static ColumnType infer(JsonElement value) {
        if (value != null && value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            return ColumnType.listOf(array.isEmpty() ? String.class : infer(array.get(0)).type());
        }
        if (value != null && value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return ColumnType.of(Boolean.class);
            }
            if (primitive.isNumber()) {
                String text = primitive.getAsString();
                return text.contains(".") || text.contains("e") || text.contains("E")
                        ? ColumnType.of(Double.class)
                        : ColumnType.of(Long.class);
            }
        }
        return ColumnType.of(String.class);
    }

    /** {@code value} as a value of {@code type}, or null for JSON null. */
    static Object convert(JsonElement value, ColumnType type) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (type.isList()) {
            List<Object> list = new ArrayList<>();
            JsonArray array = value.isJsonArray() ? value.getAsJsonArray() : wrap(value);
            ColumnType element = ColumnType.of(type.listElementType());
            for (JsonElement item : array) {
                list.add(convert(item, element));
            }
            return list;
        }
        Class<?> cls = type.type();
        if (cls == String.class) {
            return value.isJsonPrimitive() ? value.getAsString() : value.toString();
        }
        if (cls == Long.class) {
            return value.getAsLong();
        }
        if (cls == Integer.class) {
            return value.getAsInt();
        }
        if (cls == Double.class) {
            return value.getAsDouble();
        }
        if (cls == Boolean.class) {
            return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                    ? value.getAsBoolean()
                    : Boolean.parseBoolean(value.getAsString());
        }
        return value.getAsString();
    }

    /** The column's type. */
    static ColumnType typeOf(CyColumn column) {
        return new ColumnType(column.getType(), column.getListElementType());
    }

    /**
     * The column {@code name} of {@code type}, created if missing.
     *
     * @return empty if a column of that name exists with a different type
     */
    static Optional<CyColumn> column(
            CyTable table, String name, ColumnType type, Object defaultValue) {
        CyColumn existing = table.getColumn(name);
        if (existing != null) {
            return typeOf(existing).equals(type) ? Optional.of(existing) : Optional.empty();
        }
        if (type.isList()) {
            table.createListColumn(name, type.listElementType(), false, castList(defaultValue));
        } else {
            create(table, name, type.type(), defaultValue);
        }
        return Optional.of(table.getColumn(name));
    }

    @SuppressWarnings("unchecked")
    private static <T> void create(CyTable table, String name, Class<T> cls, Object defaultValue) {
        table.createColumn(name, cls, false, (T) defaultValue);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List castList(Object value) {
        return value instanceof List list ? list : null;
    }

    /**
     * Checks that an existing column {@code name} has {@code type}, before anything is written.
     *
     * @param what how the message names it, such as "updateTables column"
     */
    static void requireType(CyTable table, String name, ColumnType type, String what) {
        CyColumn existing = table.getColumn(name);
        if (existing != null && !typeOf(existing).equals(type)) {
            throw new IllegalArgumentException(
                    what
                            + " '"
                            + name
                            + "' is "
                            + describe(typeOf(existing))
                            + " in Desktop but "
                            + describe(type)
                            + " in the result");
        }
    }

    /** The CX2 type name of a column, for messages. */
    static String describe(ColumnType type) {
        return CyColumnTypes.toCx2(type).orElse(type.type().getSimpleName());
    }

    private static JsonArray wrap(JsonElement value) {
        JsonArray array = new JsonArray();
        array.add(value);
        return array;
    }
}
