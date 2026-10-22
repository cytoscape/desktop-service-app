package org.cytoscape.serviceapps.form;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A service app's dialog, described without any Swing: the header text and one control per
 * parameter and per input column.
 *
 * @param appName the app's name, shown as the title
 * @param version the app's version
 * @param citation the app's citation, or null
 * @param description the description to show, or null when the app doesn't ask for it
 * @param parameters one control per parameter, in metadata order
 * @param inputColumns one control per input column the user picks, in metadata order
 * @param autoColumns columns the app always receives, with no control
 */
public record FormSpec(
        String appName,
        String version,
        String citation,
        String description,
        List<ControlSpec> parameters,
        List<ControlSpec> inputColumns,
        List<String> autoColumns) {

    public FormSpec {
        parameters = List.copyOf(parameters);
        inputColumns = List.copyOf(inputColumns);
        autoColumns = List.copyOf(autoColumns);
    }

    /** The values the dialog starts with. */
    public FormValues initialValues() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        for (ControlSpec control : parameters) {
            params.put(control.key(), control.selected());
        }
        Map<String, List<String>> columns = new LinkedHashMap<>();
        for (ControlSpec control : inputColumns) {
            columns.put(control.key(), control.selected());
        }
        return new FormValues(params, columns);
    }

    /** Every problem with {@code values}, as messages for the user; empty when they are valid. */
    public List<String> validate(FormValues values) {
        List<String> problems = new ArrayList<>();
        for (ControlSpec control : parameters) {
            control.validate(values.parameters().get(control.key())).ifPresent(problems::add);
        }
        for (ControlSpec control : inputColumns) {
            control.validate(values.inputColumns().get(control.key())).ifPresent(problems::add);
        }
        return problems;
    }
}
