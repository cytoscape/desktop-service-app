package org.cytoscape.serviceapps.form;

import java.util.List;
import java.util.Optional;

/**
 * One control in a service app's dialog, described without any Swing.
 *
 * @param key the parameter's {@code displayName}, or the input column's {@code name}; the value is
 *     sent under this key
 * @param kind which control to draw
 * @param label the text beside the control
 * @param tooltip the hint shown on hover, or null
 * @param options the choices, for every kind except text and checkbox
 * @param selected the initial value(s): one for single-value controls, any number for a
 *     multi-select input column, none when nothing is pre-selected
 * @param multiSelect whether several options may be chosen (input columns only)
 * @param rule the validation rules for a text control, or null
 * @param noOptionsMessage shown when a choice is required but there are no options, or null
 */
public record ControlSpec(
        String key,
        ControlKind kind,
        String label,
        String tooltip,
        List<String> options,
        List<String> selected,
        boolean multiSelect,
        TextRule rule,
        String noOptionsMessage) {

    public ControlSpec {
        options = List.copyOf(options);
        selected = List.copyOf(selected);
    }

    /** Why {@code values} is not a valid answer for this control, or empty if it is. */
    public Optional<String> validate(List<String> values) {
        List<String> chosen = values == null ? List.of() : values;
        switch (kind) {
            case TEXT:
                return rule == null
                        ? Optional.empty()
                        : rule.validate(chosen.isEmpty() ? "" : chosen.get(0));
            case CHECK_BOX:
                return chosen.size() == 1
                                && ("true".equals(chosen.get(0)) || "false".equals(chosen.get(0)))
                        ? Optional.empty()
                        : Optional.of(label + " must be checked or unchecked");
            default:
                if (options.isEmpty()) {
                    return Optional.of(
                            noOptionsMessage != null
                                    ? noOptionsMessage
                                    : label + " has no choices");
                }
                if (chosen.isEmpty()) {
                    return Optional.of("Choose a value for " + label);
                }
                if (!multiSelect && chosen.size() > 1) {
                    return Optional.of("Choose one value for " + label);
                }
                for (String value : chosen) {
                    if (!options.contains(value)) {
                        return Optional.of("'" + value + "' is not a choice for " + label);
                    }
                }
                return Optional.empty();
        }
    }
}
