package org.cytoscape.serviceapps.call;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.app.AppSupport;
import org.cytoscape.serviceapps.form.ControlKind;
import org.cytoscape.serviceapps.form.ControlSpec;
import org.cytoscape.serviceapps.form.FormSpec;
import org.cytoscape.serviceapps.form.FormSpecBuilder;
import org.cytoscape.serviceapps.form.TextRule;
import org.cytoscape.serviceapps.model.Parameter;
import org.cytoscape.serviceapps.model.ServiceInputDefinition;
import org.cytoscape.serviceapps.model.ServiceMetadata;

/**
 * The JSON Schema of {@code task call}'s {@code serviceInput} for one service app: {@code
 * {"parameters": {...}, "inputColumns": {...}}}.
 *
 * <p>Built from the same {@link ControlSpec}s the dialog uses ({@link FormSpecBuilder} with no
 * network), so the two can't drift apart. Column pickers name their table and type filter rather
 * than listing columns: the columns belong to whichever network {@code task call} targets. It
 * doesn't depend on Desktop's state, so an app always gets the same schema.
 *
 * <ul>
 *   <li>{@code text}: {@code number} or {@code integer} with bounds for {@code number}/{@code
 *       digits} validation, otherwise {@code string}, with the regex as a full-match {@code
 *       pattern}.
 *   <li>{@code dropDown} and {@code radio}: {@code string} with an {@code enum} of {@code
 *       valueList}.
 *   <li>{@code checkBox}: {@code boolean}; the service receives {@code "true"} or {@code "false"}.
 *   <li>Column pickers: {@code string}, or an array of strings when several may be chosen.
 *   <li>A property is required when the metadata gives it no default. {@code checkBox} defaults to
 *       false.
 * </ul>
 */
public final class ServiceInputSchema {

    public static final String PARAMETERS = "parameters";
    public static final String INPUT_COLUMNS = "inputColumns";

    private ServiceInputSchema() {}

    public static JsonObject of(ServiceMetadata meta) {
        FormSpec spec = new FormSpecBuilder().build(meta, new JsonObject(), null);
        Map<String, Parameter> byName = new LinkedHashMap<>();
        for (Parameter parameter : meta.parameters()) {
            if (parameter.displayName != null) {
                byName.putIfAbsent(parameter.displayName, parameter);
            }
        }
        Map<String, ServiceInputDefinition.InputColumn> columnsByName = new LinkedHashMap<>();
        if (meta.serviceInputDefinition != null) {
            for (ServiceInputDefinition.InputColumn column :
                    meta.serviceInputDefinition.inputColumns()) {
                if (column.name != null) {
                    columnsByName.putIfAbsent(column.name, column);
                }
            }
        }
        String elements =
                AppSupport.INPUT_EDGE.equals(AppSupport.inputType(meta)) ? "edge" : "node";

        JsonObject parameters = new JsonObject();
        List<String> requiredParameters = new ArrayList<>();
        for (ControlSpec control : spec.parameters()) {
            Parameter parameter = byName.get(control.key());
            parameters.add(control.key(), parameter(control, parameter));
            if (required(control, parameter)) {
                requiredParameters.add(control.key());
            }
        }
        JsonObject inputColumns = new JsonObject();
        List<String> requiredColumns = new ArrayList<>();
        for (ControlSpec control : spec.inputColumns()) {
            ServiceInputDefinition.InputColumn column = columnsByName.get(control.key());
            inputColumns.add(control.key(), inputColumn(control, column, elements));
            if (column == null || blank(column.defaultColumnName)) {
                requiredColumns.add(control.key());
            }
        }

        JsonObject properties = new JsonObject();
        properties.add(
                PARAMETERS,
                Schemas.object(
                        "Parameter values, keyed by each parameter's displayName. Omitted ones take"
                                + " their default.",
                        parameters,
                        requiredParameters));
        properties.add(
                INPUT_COLUMNS,
                Schemas.object(
                        "The column(s) chosen for each input column of the target network, keyed by"
                                + " the input column's name.",
                        inputColumns,
                        requiredColumns));
        List<String> required = new ArrayList<>();
        if (!requiredParameters.isEmpty()) {
            required.add(PARAMETERS);
        }
        if (!requiredColumns.isEmpty()) {
            required.add(INPUT_COLUMNS);
        }
        String description =
                "serviceInput for "
                        + meta.name
                        + ": the values its dialog would collect. Pass it to serviceapps task call"
                        + " as a JSON string. Desktop checks values with the same rules as the dialog"
                        + " (its regex engine is Java's) and sends them to the service as text.";
        if (!spec.autoColumns().isEmpty()) {
            description +=
                    " The "
                            + elements
                            + " column(s) "
                            + String.join(", ", spec.autoColumns())
                            + " are always sent and must exist on the target network.";
        }
        return Schemas.root(description, properties, required);
    }

    private static JsonObject parameter(ControlSpec control, Parameter parameter) {
        String description =
                join(control.tooltip(), parameter == null ? null : parameter.validationHelp);
        String defaultValue = parameter == null ? null : parameter.defaultValue;
        JsonObject property;
        switch (control.kind()) {
            case TEXT:
                {
                    TextRule rule = control.rule();
                    if (TextRule.NUMBER.equals(rule.type())
                            || TextRule.DIGITS.equals(rule.type())) {
                        boolean whole = TextRule.DIGITS.equals(rule.type());
                        property = Schemas.property(whole ? "integer" : "number", description);
                        if (rule.min() != null) {
                            addNumber(property, "minimum", rule.min(), whole);
                        }
                        if (rule.max() != null) {
                            addNumber(property, "maximum", rule.max(), whole);
                        }
                        Double number = number(defaultValue);
                        if (number != null) {
                            addNumber(property, "default", number, whole);
                        }
                    } else {
                        property = Schemas.property("string", description);
                        if (!blank(rule.regex())) {
                            property.addProperty("pattern", "^(?:" + rule.regex() + ")$");
                        }
                        if (defaultValue != null) {
                            property.addProperty("default", defaultValue);
                        }
                    }
                    return property;
                }
            case DROP_DOWN:
            case RADIO:
                property = Schemas.property("string", description);
                property.add("enum", Schemas.strings(control.options()));
                if (defaultValue != null && control.options().contains(defaultValue)) {
                    property.addProperty("default", defaultValue);
                }
                return property;
            case CHECK_BOX:
                property = Schemas.property("boolean", description);
                property.addProperty(
                        "default", "true".equalsIgnoreCase(String.valueOf(defaultValue)));
                return property;
            case NODE_COLUMN:
            case EDGE_COLUMN:
                {
                    String elements = control.kind() == ControlKind.NODE_COLUMN ? "node" : "edge";
                    property =
                            Schemas.property(
                                    "string",
                                    join(
                                            description,
                                            columnHint(
                                                    elements,
                                                    parameter == null
                                                            ? null
                                                            : parameter.columnTypeFilter)));
                    if (!blank(defaultValue)) {
                        property.addProperty("default", defaultValue);
                    }
                    return property;
                }
            default:
                return Schemas.property("string", description);
        }
    }

    private static JsonObject inputColumn(
            ControlSpec control, ServiceInputDefinition.InputColumn column, String elements) {
        String description =
                join(
                        control.tooltip(),
                        columnHint(elements, column == null ? null : column.dataType));
        String defaultColumn = column == null ? null : column.defaultColumnName;
        if (control.multiSelect()) {
            JsonObject property =
                    Schemas.property("array", join(description, "One or more columns."));
            property.add("items", Schemas.property("string", null));
            property.addProperty("minItems", 1);
            if (!blank(defaultColumn)) {
                JsonArray value = new JsonArray();
                value.add(defaultColumn);
                property.add("default", value);
            }
            return property;
        }
        JsonObject property = Schemas.property("string", description);
        if (!blank(defaultColumn)) {
            property.addProperty("default", defaultColumn);
        }
        return property;
    }

    private static boolean required(ControlSpec control, Parameter parameter) {
        String defaultValue = parameter == null ? null : parameter.defaultValue;
        switch (control.kind()) {
            case CHECK_BOX:
                return false;
            case TEXT:
                return defaultValue == null && control.rule().validate("").isPresent();
            case DROP_DOWN:
            case RADIO:
                return defaultValue == null || !control.options().contains(defaultValue);
            default:
                return blank(defaultValue);
        }
    }

    /** "A node column of the target network whose type is number." */
    static String columnHint(String elements, String filter) {
        String hint =
                "The name of "
                        + ("edge".equals(elements) ? "an edge" : "a node")
                        + " column of the target network";
        return blank(filter) ? hint + "." : hint + " whose type is " + filter + ".";
    }

    private static void addNumber(JsonObject property, String key, double value, boolean whole) {
        if (whole || value == Math.rint(value) && !Double.isInfinite(value)) {
            property.addProperty(key, (long) value);
        } else {
            property.addProperty(key, value);
        }
    }

    private static Double number(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Double.parseDouble(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String join(String first, String second) {
        if (blank(first)) {
            return blank(second) ? null : second;
        }
        if (blank(second)) {
            return first;
        }
        return first.strip().endsWith(".")
                ? first.strip() + " " + second
                : first.strip() + ". " + second;
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
