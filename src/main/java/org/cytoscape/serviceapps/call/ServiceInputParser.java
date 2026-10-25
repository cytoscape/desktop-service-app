package org.cytoscape.serviceapps.call;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.serviceapps.app.AppSupport;
import org.cytoscape.serviceapps.form.ControlKind;
import org.cytoscape.serviceapps.form.ControlSpec;
import org.cytoscape.serviceapps.form.FormSpec;
import org.cytoscape.serviceapps.form.FormSpecBuilder;
import org.cytoscape.serviceapps.form.FormValues;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.request.ServiceInput;

/**
 * Checks {@code task call}'s {@code serviceInput} with the dialog's own rules and turns it into the
 * run's input. No schema-validator library: the rules are the {@link ControlSpec}s that {@link
 * FormSpecBuilder} builds for the target network, as the dialog does.
 *
 * <ul>
 *   <li>Keys are the parameters' keys ({@link org.cytoscape.serviceapps.model.ParameterKeys}) and
 *       the input columns' names; unknown keys are rejected.
 *   <li>Omitted keys take what the dialog would start with (its defaults), without remembered
 *       values.
 *   <li>A typed value or its text form is accepted; the service always receives text, and a
 *       checkbox sends {@code "true"} or {@code "false"}.
 *   <li>Every problem is reported, each naming its field.
 * </ul>
 */
public class ServiceInputParser {

    private final FormSpecBuilder forms;

    public ServiceInputParser(FormSpecBuilder forms) {
        this.forms = forms;
    }

    /**
     * @param serviceInput the argument as text; null or blank for an app that takes no input
     * @param target the network the input data is read from, or null
     * @throws InvalidInputException naming each field that was rejected
     */
    public ServiceInput parse(String serviceInput, ServiceMetadata meta, CyNetwork target) {
        JsonObject json = JsonArgument.object(serviceInput, "serviceInput");
        FormSpec spec = forms.build(meta, new JsonObject(), target);
        List<String> problems = new ArrayList<>();
        for (String key : json.keySet()) {
            if (!ServiceInputSchema.PARAMETERS.equals(key)
                    && !ServiceInputSchema.INPUT_COLUMNS.equals(key)) {
                problems.add(
                        "unknown field '"
                                + key
                                + "'; serviceInput has only parameters and inputColumns");
            }
        }

        FormValues defaults = spec.initialValues();
        Map<String, List<String>> parameters = new LinkedHashMap<>(defaults.parameters());
        Map<String, List<String>> columns = new LinkedHashMap<>(defaults.inputColumns());
        read(json, ServiceInputSchema.PARAMETERS, spec.parameters(), parameters, problems);
        read(json, ServiceInputSchema.INPUT_COLUMNS, spec.inputColumns(), columns, problems);
        if (!problems.isEmpty()) {
            throw new InvalidInputException("serviceInput", problems);
        }

        for (ControlSpec control : spec.parameters()) {
            check(control, parameters.get(control.key()), problems);
        }
        for (ControlSpec control : spec.inputColumns()) {
            check(control, columns.get(control.key()), problems);
        }
        checkAutoColumns(meta, spec, target, problems);
        if (!problems.isEmpty()) {
            throw new InvalidInputException("serviceInput", problems);
        }
        return new FormValues(parameters, columns).toServiceInput();
    }

    private static void read(
            JsonObject json,
            String section,
            List<ControlSpec> controls,
            Map<String, List<String>> values,
            List<String> problems) {
        JsonElement given = json.get(section);
        if (given == null || given.isJsonNull()) {
            return;
        }
        if (!given.isJsonObject()) {
            problems.add(section + ": must be an object keyed by name");
            return;
        }
        Map<String, ControlSpec> byKey = new LinkedHashMap<>();
        controls.forEach(control -> byKey.put(control.key(), control));
        for (Map.Entry<String, JsonElement> entry : given.getAsJsonObject().entrySet()) {
            ControlSpec control = byKey.get(entry.getKey());
            if (control == null) {
                problems.add(
                        section
                                + ": unknown "
                                + (ServiceInputSchema.PARAMETERS.equals(section)
                                        ? "parameter '"
                                        : "input column '")
                                + entry.getKey()
                                + "'; serviceapps describe lists the accepted names");
                continue;
            }
            coerce(control, entry.getValue())
                    .ifPresentOrElse(
                            value -> values.put(control.key(), value),
                            () ->
                                    problems.add(
                                            control.label()
                                                    + ": "
                                                    + expected(control)
                                                    + ", not "
                                                    + entry.getValue()));
        }
    }

    /** The value as the dialog would hold it, or empty when its JSON type is wrong. */
    private static Optional<List<String>> coerce(ControlSpec control, JsonElement value) {
        if (control.kind() == ControlKind.CHECK_BOX) {
            if (value.isJsonPrimitive()) {
                JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (primitive.isBoolean()) {
                    return Optional.of(List.of(Boolean.toString(primitive.getAsBoolean())));
                }
                if (primitive.isString()
                        && ("true".equalsIgnoreCase(primitive.getAsString())
                                || "false".equalsIgnoreCase(primitive.getAsString()))) {
                    return Optional.of(List.of(primitive.getAsString().toLowerCase(Locale.ROOT)));
                }
            }
            return Optional.empty();
        }
        if (control.kind() == ControlKind.INPUT_COLUMN && value.isJsonArray()) {
            List<String> names = new ArrayList<>();
            for (JsonElement item : value.getAsJsonArray()) {
                if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                    return Optional.empty();
                }
                names.add(item.getAsString());
            }
            return Optional.of(names);
        }
        if (value.isJsonPrimitive() && !value.getAsJsonPrimitive().isBoolean()) {
            return Optional.of(List.of(value.getAsString()));
        }
        return Optional.empty();
    }

    private static String expected(ControlSpec control) {
        switch (control.kind()) {
            case CHECK_BOX:
                return "must be true or false";
            case INPUT_COLUMN:
                return control.multiSelect()
                        ? "must be a column name or a list of column names"
                        : "must be a column name";
            default:
                return "must be a single value";
        }
    }

    private static void check(ControlSpec control, List<String> value, List<String> problems) {
        control.validate(value)
                .ifPresent(
                        problem ->
                                problems.add(
                                        problem.contains(control.label())
                                                ? problem
                                                : control.label() + ": " + problem));
    }

    /** Columns the app always sends must exist on the target network. */
    private static void checkAutoColumns(
            ServiceMetadata meta, FormSpec spec, CyNetwork target, List<String> problems) {
        if (spec.autoColumns().isEmpty() || target == null) {
            return;
        }
        boolean nodes = !AppSupport.INPUT_EDGE.equals(AppSupport.inputType(meta));
        CyTable table = nodes ? target.getDefaultNodeTable() : target.getDefaultEdgeTable();
        for (String column : spec.autoColumns()) {
            if (table.getColumn(column) == null) {
                problems.add(
                        "the target network has no "
                                + (nodes ? "node" : "edge")
                                + " column named '"
                                + column
                                + "', which "
                                + meta.name
                                + " always sends");
            }
        }
    }
}
