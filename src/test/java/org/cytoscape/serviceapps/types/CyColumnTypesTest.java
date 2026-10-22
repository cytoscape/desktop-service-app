package org.cytoscape.serviceapps.types;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.cytoscape.serviceapps.types.CyColumnTypes.ColumnType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CyColumnTypesTest {

    private static final ColumnType STRING = ColumnType.of(String.class);
    private static final ColumnType LONG = ColumnType.of(Long.class);
    private static final ColumnType INTEGER = ColumnType.of(Integer.class);
    private static final ColumnType DOUBLE = ColumnType.of(Double.class);
    private static final ColumnType BOOLEAN = ColumnType.of(Boolean.class);
    private static final ColumnType LIST_STRING = ColumnType.listOf(String.class);
    private static final ColumnType LIST_LONG = ColumnType.listOf(Long.class);
    private static final ColumnType LIST_INTEGER = ColumnType.listOf(Integer.class);
    private static final ColumnType LIST_DOUBLE = ColumnType.listOf(Double.class);
    private static final ColumnType LIST_BOOLEAN = ColumnType.listOf(Boolean.class);

    private static final List<ColumnType> ALL =
            List.of(
                    STRING,
                    LONG,
                    INTEGER,
                    DOUBLE,
                    BOOLEAN,
                    LIST_STRING,
                    LIST_LONG,
                    LIST_INTEGER,
                    LIST_DOUBLE,
                    LIST_BOOLEAN);

    private static final Map<String, ColumnType> CX2 =
            Map.of(
                    "string", STRING,
                    "long", LONG,
                    "integer", INTEGER,
                    "double", DOUBLE,
                    "boolean", BOOLEAN,
                    "list_of_string", LIST_STRING,
                    "list_of_long", LIST_LONG,
                    "list_of_integer", LIST_INTEGER,
                    "list_of_double", LIST_DOUBLE,
                    "list_of_boolean", LIST_BOOLEAN);

    @Test
    void mapsEveryCx2TypeBothWays() {
        CX2.forEach(
                (name, type) -> {
                    assertEquals(type, CyColumnTypes.fromCx2(name).orElseThrow(), name);
                    assertEquals(name, CyColumnTypes.toCx2(type).orElseThrow(), name);
                });
    }

    @Test
    void ignoresCaseAndSpaces() {
        assertEquals(LIST_DOUBLE, CyColumnTypes.fromCx2(" List_Of_Double ").orElseThrow());
    }

    @Test
    void rejectsNamesThatAreNotCx2Types() {
        for (String name : new String[] {"number", "list", "float", "list_of_float", "", null}) {
            assertTrue(CyColumnTypes.fromCx2(name).isEmpty(), String.valueOf(name));
        }
        assertTrue(CyColumnTypes.toCx2(ColumnType.of(Float.class)).isEmpty());
    }

    static Stream<Arguments> filters() {
        return Stream.of(
                Arguments.of(null, ALL),
                Arguments.of("", ALL),
                Arguments.of("number", List.of(LONG, INTEGER, DOUBLE)),
                Arguments.of("wholenumber", List.of(LONG, INTEGER)),
                Arguments.of(
                        "list",
                        List.of(LIST_STRING, LIST_LONG, LIST_INTEGER, LIST_DOUBLE, LIST_BOOLEAN)),
                Arguments.of("list_of_number", List.of(LIST_LONG, LIST_INTEGER, LIST_DOUBLE)),
                Arguments.of("list_of_wholenumber", List.of(LIST_LONG, LIST_INTEGER)),
                Arguments.of("string", List.of(STRING)),
                Arguments.of("long", List.of(LONG)),
                Arguments.of("integer", List.of(INTEGER)),
                Arguments.of("double", List.of(DOUBLE)),
                Arguments.of("boolean", List.of(BOOLEAN)),
                Arguments.of("list_of_string", List.of(LIST_STRING)),
                Arguments.of("list_of_long", List.of(LIST_LONG)),
                Arguments.of("list_of_integer", List.of(LIST_INTEGER)),
                Arguments.of("list_of_double", List.of(LIST_DOUBLE)),
                Arguments.of("list_of_boolean", List.of(LIST_BOOLEAN)),
                Arguments.of("nonsense", List.of()));
    }

    @ParameterizedTest(name = "filter [{0}]")
    @MethodSource("filters")
    void eachFilterMatchesExactlyItsTypes(String filter, List<ColumnType> expected) {
        for (ColumnType type : ALL) {
            assertEquals(
                    expected.contains(type),
                    CyColumnTypes.matches(filter, type),
                    filter + " vs " + type);
        }
    }

    @Test
    void numberFiltersNeverMatchLists() {
        assertFalse(CyColumnTypes.matches("number", LIST_DOUBLE));
        assertFalse(CyColumnTypes.matches("list_of_number", DOUBLE));
    }
}
