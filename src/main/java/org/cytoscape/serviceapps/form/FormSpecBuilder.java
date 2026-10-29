package org.cytoscape.serviceapps.form;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonObject;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.serviceapps.app.AppSupport;
import org.cytoscape.serviceapps.model.Parameter;
import org.cytoscape.serviceapps.model.ParameterKeys;
import org.cytoscape.serviceapps.model.ServiceInputDefinition;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.types.CyColumnTypes;

/**
 * Builds a service app's dialog from its metadata. A pure function: everything it needs is passed
 * in, and it reads no stored state.
 *
 * <p>Follows the spec's <a
 * href="https://github.com/cytoscape/cytoscape-web/wiki/parameters">parameters</a> and <a
 * href="https://github.com/cytoscape/cytoscape-web/wiki/serviceInputDefinition.inputColumns">inputColumns</a>
 * pages. Remembered values replace defaults when they are still valid; stale ones are ignored.
 */
public class FormSpecBuilder {

    /**
     * @param lastValues the values used last time, in {@link FormValues} form; may be empty
     * @param target the network whose columns the column pickers list; null when none is open
     */
    public FormSpec build(ServiceMetadata meta, JsonObject lastValues, CyNetwork target) {
        FormValues last = FormValues.fromJson(lastValues);

        List<ControlSpec> parameters = new ArrayList<>();
        List<String> keys = ParameterKeys.of(meta.parameters());
        for (int i = 0; i < keys.size(); i++) {
            // Of parameters that still share a key, only the last is used, as in Cytoscape Web.
            if (keys.lastIndexOf(keys.get(i)) != i) {
                continue;
            }
            ControlSpec control = parameter(meta.parameters().get(i), keys.get(i), target, last);
            if (control != null) {
                parameters.add(control);
            }
        }

        List<ControlSpec> inputColumns = new ArrayList<>();
        List<String> autoColumns = new ArrayList<>();
        ServiceInputDefinition input = meta.serviceInputDefinition;
        String type = AppSupport.inputType(meta);
        if (input != null
                && (AppSupport.INPUT_NODE.equals(type) || AppSupport.INPUT_EDGE.equals(type))) {
            CyTable table = table(target, AppSupport.INPUT_NODE.equals(type));
            for (ServiceInputDefinition.InputColumn column : input.inputColumns()) {
                if (column.columnName != null && !column.columnName.isBlank()) {
                    autoColumns.add(column.columnName);
                } else if (column.name != null && !column.name.isBlank()) {
                    inputColumns.add(inputColumn(column, table, type, last));
                }
            }
        }

        boolean showDescription = Boolean.TRUE.equals(meta.showDescriptionInDialog);
        return new FormSpec(
                meta.name,
                meta.version,
                blankToNull(meta.citation),
                showDescription ? blankToNull(meta.description) : null,
                parameters,
                inputColumns,
                autoColumns);
    }

    private ControlSpec parameter(
            Parameter parameter, String key, CyNetwork target, FormValues last) {
        if (parameter.displayName == null || parameter.type == null) {
            return null;
        }
        String label = parameter.displayName;
        List<String> groups = parameter.groups();
        String tooltip = blankToNull(parameter.description);
        String remembered = first(last.parameters().get(key));
        List<String> values = parameter.valueList == null ? List.of() : parameter.valueList;

        switch (parameter.type) {
            case Parameter.TEXT:
                {
                    TextRule rule =
                            new TextRule(
                                    parameter.validationType,
                                    parameter.validationRegex,
                                    parameter.minValue,
                                    parameter.maxValue,
                                    parameter.validationHelp);
                    String value =
                            remembered != null && rule.validate(remembered).isEmpty()
                                    ? remembered
                                    : (parameter.defaultValue == null
                                            ? ""
                                            : parameter.defaultValue);
                    return new ControlSpec(
                            key,
                            ControlKind.TEXT,
                            label,
                            tooltip,
                            List.of(),
                            List.of(value),
                            false,
                            rule,
                            null,
                            groups);
                }
            case Parameter.DROP_DOWN:
            case Parameter.RADIO:
                return new ControlSpec(
                        key,
                        Parameter.DROP_DOWN.equals(parameter.type)
                                ? ControlKind.DROP_DOWN
                                : ControlKind.RADIO,
                        label,
                        tooltip,
                        values,
                        choose(values, remembered, parameter.defaultValue),
                        false,
                        null,
                        label + " has no choices",
                        groups);
            case Parameter.CHECK_BOX:
                {
                    String value =
                            "true".equals(remembered) || "false".equals(remembered)
                                    ? remembered
                                    : Boolean.toString(
                                            "true"
                                                    .equalsIgnoreCase(
                                                            String.valueOf(
                                                                    parameter.defaultValue)));
                    return new ControlSpec(
                            key,
                            ControlKind.CHECK_BOX,
                            label,
                            tooltip,
                            List.of(),
                            List.of(value),
                            false,
                            null,
                            null,
                            groups);
                }
            case Parameter.NODE_COLUMN:
            case Parameter.EDGE_COLUMN:
                {
                    boolean nodes = Parameter.NODE_COLUMN.equals(parameter.type);
                    CyTable table = table(target, nodes);
                    List<String> columns = columnNames(table, parameter.columnTypeFilter());
                    return new ControlSpec(
                            key,
                            nodes ? ControlKind.NODE_COLUMN : ControlKind.EDGE_COLUMN,
                            label,
                            tooltip,
                            columns,
                            choose(columns, remembered, parameter.defaultValue),
                            false,
                            null,
                            noColumnsMessage(
                                    table,
                                    nodes ? "node" : "edge",
                                    parameter.columnTypeFilter(),
                                    label),
                            groups);
                }
            default:
                // ndexUUID gets no control: RequestBuilder fills it. file, accessToken and unknown
                // types get none either, and AppSupport keeps apps that use them from running.
                return null;
        }
    }

    private ControlSpec inputColumn(
            ServiceInputDefinition.InputColumn column,
            CyTable table,
            String type,
            FormValues last) {
        List<String> columns = columnNames(table, CyColumnTypes.filters(column.dataType));
        boolean multi = Boolean.TRUE.equals(column.allowMultipleSelection);
        List<String> remembered = last.inputColumns().get(column.name);
        List<String> selected;
        if (remembered != null
                && !remembered.isEmpty()
                && columns.containsAll(remembered)
                && (multi || remembered.size() == 1)) {
            selected = remembered;
        } else if (column.defaultColumnName != null && columns.contains(column.defaultColumnName)) {
            selected = List.of(column.defaultColumnName);
        } else {
            selected = List.of();
        }
        return new ControlSpec(
                column.name,
                ControlKind.INPUT_COLUMN,
                column.name,
                blankToNull(column.description),
                columns,
                selected,
                multi,
                null,
                noColumnsMessage(table, type, CyColumnTypes.filters(column.dataType), column.name),
                List.of());
    }

    private static List<String> choose(List<String> options, String remembered, String fallback) {
        if (remembered != null && options.contains(remembered)) {
            return List.of(remembered);
        }
        if (fallback != null && options.contains(fallback)) {
            return List.of(fallback);
        }
        return List.of();
    }

    /** Column names of {@code table} that pass any of {@code filters}, sorted, without SUID. */
    static List<String> columnNames(CyTable table, List<String> filters) {
        if (table == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (CyColumn column : table.getColumns()) {
            if (!CyIdentifiable.SUID.equals(column.getName())
                    && CyColumnTypes.matchesAny(filters, column)) {
                names.add(column.getName());
            }
        }
        names.sort(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)));
        return names;
    }

    private static CyTable table(CyNetwork network, boolean nodes) {
        if (network == null) {
            return null;
        }
        return nodes ? network.getDefaultNodeTable() : network.getDefaultEdgeTable();
    }

    /** The message shown when a column picker has nothing to offer. */
    private static String noColumnsMessage(
            CyTable table, String elements, List<String> filters, String label) {
        if (table == null) {
            return label + " needs a network: open one first";
        }
        String type = filters.isEmpty() ? "" : " of type " + CyColumnTypes.describe(filters);
        return "The current network has no " + elements + " column" + type + " for " + label;
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
