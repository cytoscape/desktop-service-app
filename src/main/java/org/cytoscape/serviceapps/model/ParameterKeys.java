package org.cytoscape.serviceapps.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The key each parameter's value is sent and remembered under, by Cytoscape Web's rule: the {@code
 * displayName}, or, for parameters that share a {@code displayName}, the group path and the name
 * joined by {@code /}, such as {@code Clusters/Gap}.
 */
public final class ParameterKeys {

    public static final String SEPARATOR = "/";

    private ParameterKeys() {}

    /** One key per parameter, in list order. */
    public static List<String> of(List<Parameter> parameters) {
        Map<String, Integer> counts = new HashMap<>();
        for (Parameter parameter : parameters) {
            counts.merge(String.valueOf(parameter.displayName), 1, Integer::sum);
        }
        List<String> keys = new ArrayList<>();
        for (Parameter parameter : parameters) {
            if (counts.get(String.valueOf(parameter.displayName)) > 1) {
                List<String> path = new ArrayList<>(parameter.groups());
                path.add(parameter.displayName);
                keys.add(String.join(SEPARATOR, path));
            } else {
                keys.add(parameter.displayName);
            }
        }
        return keys;
    }

    /**
     * Keys still shared after the rule: parameters with the same name in the same groups. Only the
     * last of each is used, as in Cytoscape Web.
     */
    public static List<String> duplicates(List<Parameter> parameters) {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (String key : of(parameters)) {
            if (!seen.add(key) && !duplicates.contains(key)) {
                duplicates.add(key);
            }
        }
        return duplicates;
    }
}
