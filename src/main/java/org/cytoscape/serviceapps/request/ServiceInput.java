package org.cytoscape.serviceapps.request;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The values a user (through the dialog) or a caller (through {@code task call}) gives for one run.
 *
 * @param parameters one value per parameter, keyed by {@code displayName}
 * @param inputColumns the chosen column names per input column, keyed by the input column's {@code
 *     name}; input columns with a fixed {@code columnName} are added by the request builder
 */
public record ServiceInput(Map<String, String> parameters, Map<String, List<String>> inputColumns) {

    public ServiceInput {
        parameters = Map.copyOf(new LinkedHashMap<>(parameters));
        Map<String, List<String>> columns = new LinkedHashMap<>();
        inputColumns.forEach((key, value) -> columns.put(key, List.copyOf(value)));
        inputColumns = Map.copyOf(columns);
    }

    public static ServiceInput empty() {
        return new ServiceInput(Map.of(), Map.of());
    }
}
