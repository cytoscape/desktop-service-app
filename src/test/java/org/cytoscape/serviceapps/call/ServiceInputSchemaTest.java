package org.cytoscape.serviceapps.call;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.fixture.ScenarioCatalog;
import org.cytoscape.serviceapps.form.ControlSpec;
import org.cytoscape.serviceapps.form.FormSpec;
import org.cytoscape.serviceapps.form.FormSpecBuilder;
import org.cytoscape.serviceapps.model.Parameter;
import org.cytoscape.serviceapps.model.ServiceInputDefinition;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.testsupport.TestNetworks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code describe}'s {@code serviceInputSchema} matches the dialog's {@code FormSpec} and the
 * spec's metadata rules, for every fixture scenario.
 */
class ServiceInputSchemaTest {

    static Stream<String> scenarios() {
        return ScenarioCatalog.all().keySet().stream().sorted();
    }

    private static JsonObject section(JsonObject schema, String name) {
        return schema.getAsJsonObject("properties").getAsJsonObject(name);
    }

    private static List<String> keys(JsonObject object) {
        return new ArrayList<>(object.getAsJsonObject("properties").keySet());
    }

    private static List<String> strings(JsonElement array) {
        List<String> values = new ArrayList<>();
        array.getAsJsonArray().forEach(e -> values.add(e.getAsString()));
        return values;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void everyPropertyMatchesTheDialogAndTheMetadata(String scenario) {
        ServiceMetadata meta = TestNetworks.scenario(scenario);
        FormSpec spec = new FormSpecBuilder().build(meta, new JsonObject(), null);
        JsonObject schema = ServiceInputSchema.of(meta);

        assertEquals("object", schema.get("type").getAsString());
        assertFalse(schema.get("additionalProperties").getAsBoolean());
        JsonObject parameters = section(schema, "parameters");
        JsonObject columns = section(schema, "inputColumns");
        assertEquals(spec.parameters().stream().map(ControlSpec::key).toList(), keys(parameters));
        assertEquals(spec.inputColumns().stream().map(ControlSpec::key).toList(), keys(columns));
        assertFalse(parameters.get("additionalProperties").getAsBoolean());

        List<String> required = strings(parameters.get("required"));
        for (Parameter parameter : meta.parameters()) {
            JsonObject property =
                    parameters.getAsJsonObject("properties").getAsJsonObject(parameter.displayName);
            if (Parameter.UNSUPPORTED_TYPES.contains(parameter.type)) {
                assertNull(property, "unsupported types are left out: " + parameter.displayName);
                continue;
            }
            assertNotNull(property, parameter.displayName);
            assertParameter(parameter, property, required.contains(parameter.displayName));
        }

        List<String> requiredColumns = strings(columns.get("required"));
        if (meta.serviceInputDefinition != null) {
            for (ServiceInputDefinition.InputColumn column :
                    meta.serviceInputDefinition.inputColumns()) {
                JsonObject property =
                        column.name == null
                                ? null
                                : columns.getAsJsonObject("properties")
                                        .getAsJsonObject(column.name);
                if (column.columnName != null && !column.columnName.isBlank()) {
                    assertNull(property, "always-sent columns have no property");
                    assertTrue(
                            schema.get("description").getAsString().contains(column.columnName),
                            "the description names the always-sent column");
                    continue;
                }
                if (property == null) {
                    continue; // not a node or edge app: no pickers
                }
                assertInputColumn(column, property, requiredColumns.contains(column.name));
            }
        }
    }

    private static void assertParameter(Parameter p, JsonObject property, boolean required) {
        String type = property.get("type").getAsString();
        String description =
                property.has("description") ? property.get("description").getAsString() : "";
        if (p.description != null) {
            assertTrue(description.contains(p.description), description);
        }
        switch (p.type) {
            case Parameter.TEXT -> {
                String validation = p.validationType == null ? "string" : p.validationType;
                if (validation.equals("number") || validation.equals("digits")) {
                    assertEquals(validation.equals("digits") ? "integer" : "number", type);
                    if (p.minValue != null) {
                        assertEquals(p.minValue, property.get("minimum").getAsDouble());
                    }
                    if (p.maxValue != null) {
                        assertEquals(p.maxValue, property.get("maximum").getAsDouble());
                    }
                    if (p.defaultValue != null) {
                        assertEquals(
                                Double.parseDouble(p.defaultValue),
                                property.get("default").getAsDouble());
                    }
                } else {
                    assertEquals("string", type);
                    if (p.validationRegex != null && !p.validationRegex.isEmpty()) {
                        assertEquals(
                                "^(?:" + p.validationRegex + ")$",
                                property.get("pattern").getAsString());
                    } else {
                        assertFalse(property.has("pattern"));
                    }
                    if (p.defaultValue != null) {
                        assertEquals(p.defaultValue, property.get("default").getAsString());
                    }
                }
                if (p.validationHelp != null) {
                    assertTrue(description.contains(p.validationHelp), description);
                }
                boolean emptyIsInvalid =
                        validation.equals("number")
                                || validation.equals("digits")
                                || (p.validationRegex != null && !"".matches(p.validationRegex));
                assertEquals(p.defaultValue == null && emptyIsInvalid, required, "required");
            }
            case Parameter.DROP_DOWN, Parameter.RADIO -> {
                assertEquals("string", type);
                assertEquals(p.valueList, strings(property.get("enum")));
                boolean hasDefault = p.defaultValue != null && p.valueList.contains(p.defaultValue);
                assertEquals(hasDefault, property.has("default"));
                assertEquals(!hasDefault, required, "required");
            }
            case Parameter.CHECK_BOX -> {
                assertEquals("boolean", type);
                assertEquals(
                        "true".equalsIgnoreCase(String.valueOf(p.defaultValue)),
                        property.get("default").getAsBoolean());
                assertFalse(required);
            }
            case Parameter.NODE_COLUMN, Parameter.EDGE_COLUMN -> {
                assertEquals("string", type);
                String elements =
                        Parameter.NODE_COLUMN.equals(p.type) ? "a node column" : "an edge column";
                assertTrue(description.contains(elements), description);
                if (p.columnTypeFilter != null && !p.columnTypeFilter.isBlank()) {
                    assertTrue(
                            description.contains("whose type is " + p.columnTypeFilter),
                            description);
                }
                assertFalse(property.has("enum"), "column names belong to the target network");
                boolean hasDefault = p.defaultValue != null && !p.defaultValue.isBlank();
                assertEquals(hasDefault, property.has("default"));
                assertEquals(!hasDefault, required, "required");
            }
            default -> throw new AssertionError("unexpected type " + p.type);
        }
    }

    private static void assertInputColumn(
            ServiceInputDefinition.InputColumn column, JsonObject property, boolean required) {
        boolean multi = Boolean.TRUE.equals(column.allowMultipleSelection);
        assertEquals(multi ? "array" : "string", property.get("type").getAsString());
        if (multi) {
            assertEquals("string", property.getAsJsonObject("items").get("type").getAsString());
        }
        String description = property.get("description").getAsString();
        if (column.dataType != null) {
            assertTrue(description.contains("whose type is " + column.dataType), description);
        }
        boolean hasDefault =
                column.defaultColumnName != null && !column.defaultColumnName.isBlank();
        assertEquals(hasDefault, property.has("default"));
        if (hasDefault) {
            JsonElement value = property.get("default");
            assertEquals(
                    column.defaultColumnName,
                    multi ? value.getAsJsonArray().get(0).getAsString() : value.getAsString());
        }
        assertEquals(!hasDefault, required, "required");
    }

    @Test
    void anAppAlwaysGetsTheSameSchema() {
        ServiceMetadata meta = TestNetworks.scenario("param-node-column-filters");

        assertEquals(ServiceInputSchema.of(meta), ServiceInputSchema.of(meta));
    }

    @Test
    void anAppWithoutInputsHasEmptySectionsAndNothingRequired() {
        JsonObject schema = ServiceInputSchema.of(TestNetworks.scenario("unsupported-file"));

        assertEquals(new JsonArray(), schema.get("required"));
        assertTrue(keys(section(schema, "parameters")).isEmpty());
    }

    @Test
    void theUiInputSchemaHasTheTwoOptionalIds() {
        JsonObject schema = UiTargetResolver.schema();

        assertEquals(List.of("networkId", "networkViewId"), keys(schema));
        assertEquals(new JsonArray(), schema.get("required"));
        assertEquals(
                "integer",
                schema.getAsJsonObject("properties")
                        .getAsJsonObject("networkId")
                        .get("type")
                        .getAsString());
    }
}
