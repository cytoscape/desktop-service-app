package org.cytoscape.serviceapps.types;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.cytoscape.model.CyColumn;

/**
 * Maps CX2 data types, and the type filters service apps use, to Desktop column types and back.
 *
 * <p>The CX2 types are {@code string}, {@code long}, {@code integer}, {@code double}, {@code
 * boolean} and their {@code list_of_*} forms. The filters add {@code number}, {@code wholenumber},
 * {@code list}, {@code list_of_number} and {@code list_of_wholenumber}.
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/parameters">parameters:
 *     columnTypeFilter</a>
 */
public final class CyColumnTypes {

    /** A Desktop column type: the value class, and for a {@code List} its element class. */
    public record ColumnType(Class<?> type, Class<?> listElementType) {
        public static ColumnType of(Class<?> type) {
            return new ColumnType(type, null);
        }

        public static ColumnType listOf(Class<?> elementType) {
            return new ColumnType(List.class, elementType);
        }

        public boolean isList() {
            return List.class.equals(type);
        }
    }

    public static final String NUMBER = "number";
    public static final String WHOLE_NUMBER = "wholenumber";
    public static final String LIST = "list";
    public static final String LIST_OF_NUMBER = "list_of_number";
    public static final String LIST_OF_WHOLE_NUMBER = "list_of_wholenumber";

    private static final String LIST_PREFIX = "list_of_";

    private static final Map<String, Class<?>> SCALARS =
            Map.of(
                    "string", String.class,
                    "long", Long.class,
                    "integer", Integer.class,
                    "double", Double.class,
                    "boolean", Boolean.class);

    private static final Set<Class<?>> NUMBERS = Set.of(Integer.class, Long.class, Double.class);
    private static final Set<Class<?>> WHOLE_NUMBERS = Set.of(Integer.class, Long.class);

    private CyColumnTypes() {}

    /** The Desktop column type for a CX2 type, or empty if the name is not a CX2 type. */
    public static Optional<ColumnType> fromCx2(String cx2Type) {
        if (cx2Type == null) {
            return Optional.empty();
        }
        String name = cx2Type.strip().toLowerCase(Locale.ROOT);
        if (name.startsWith(LIST_PREFIX)) {
            Class<?> element = SCALARS.get(name.substring(LIST_PREFIX.length()));
            return element == null ? Optional.empty() : Optional.of(ColumnType.listOf(element));
        }
        Class<?> scalar = SCALARS.get(name);
        return scalar == null ? Optional.empty() : Optional.of(ColumnType.of(scalar));
    }

    /** The CX2 type name for a Desktop column type, or empty if CX2 has no such type. */
    public static Optional<String> toCx2(ColumnType columnType) {
        if (columnType.isList()) {
            return scalarName(columnType.listElementType()).map(name -> LIST_PREFIX + name);
        }
        return scalarName(columnType.type());
    }

    public static Optional<String> toCx2(CyColumn column) {
        return toCx2(new ColumnType(column.getType(), column.getListElementType()));
    }

    /**
     * Whether a column type passes a filter: a CX2 type name, one of the grouping filters, or null
     * or blank for any type. An unknown filter matches nothing.
     */
    public static boolean matches(String filter, ColumnType columnType) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String name = filter.strip().toLowerCase(Locale.ROOT);
        switch (name) {
            case NUMBER:
                return !columnType.isList() && NUMBERS.contains(columnType.type());
            case WHOLE_NUMBER:
                return !columnType.isList() && WHOLE_NUMBERS.contains(columnType.type());
            case LIST:
                return columnType.isList();
            case LIST_OF_NUMBER:
                return columnType.isList() && NUMBERS.contains(columnType.listElementType());
            case LIST_OF_WHOLE_NUMBER:
                return columnType.isList() && WHOLE_NUMBERS.contains(columnType.listElementType());
            default:
                return fromCx2(name).map(columnType::equals).orElse(false);
        }
    }

    public static boolean matches(String filter, CyColumn column) {
        return matches(filter, new ColumnType(column.getType(), column.getListElementType()));
    }

    /**
     * Whether a column type passes any of the filters, as Cytoscape Web reads a {@code
     * columnTypeFilter} list. An empty list matches any type.
     */
    public static boolean matchesAny(List<String> filters, ColumnType columnType) {
        return filters.isEmpty() || filters.stream().anyMatch(f -> matches(f, columnType));
    }

    public static boolean matchesAny(List<String> filters, CyColumn column) {
        return matchesAny(filters, new ColumnType(column.getType(), column.getListElementType()));
    }

    /** An input column's single {@code dataType} as a filter list; empty when there is none. */
    public static List<String> filters(String dataType) {
        return dataType == null || dataType.isBlank() ? List.of() : List.of(dataType);
    }

    /** The filters for a message: "number", or "number or list_of_string". */
    public static String describe(List<String> filters) {
        return String.join(" or ", filters);
    }

    private static Optional<String> scalarName(Class<?> type) {
        return SCALARS.entrySet().stream()
                .filter(entry -> entry.getValue().equals(type))
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
