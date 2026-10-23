package org.cytoscape.serviceapps.form;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.request.ServiceInput;

/**
 * The values entered in a service app's dialog.
 *
 * <p>Stored as the app's remembered values in the form {@code {"parameters": {key: value},
 * "inputColumns": {key: [column, ...]}}}.
 *
 * @param parameters the value(s) per parameter key
 * @param inputColumns the chosen column(s) per input column key
 */
public record FormValues(
        Map<String, List<String>> parameters, Map<String, List<String>> inputColumns) {

    public static final String PARAMETERS = "parameters";
    public static final String INPUT_COLUMNS = "inputColumns";

    public FormValues {
        parameters = copy(parameters);
        inputColumns = copy(inputColumns);
    }

    /** The values as the run's input: one value per parameter. */
    public ServiceInput toServiceInput() {
        Map<String, String> single = new LinkedHashMap<>();
        parameters.forEach((key, values) -> single.put(key, values.isEmpty() ? "" : values.get(0)));
        return new ServiceInput(single, inputColumns);
    }

    /** The values in the remembered-values form. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonObject params = new JsonObject();
        parameters.forEach(
                (key, values) -> {
                    if (!values.isEmpty()) {
                        params.addProperty(key, values.get(0));
                    }
                });
        JsonObject columns = new JsonObject();
        inputColumns.forEach(
                (key, values) -> {
                    JsonArray array = new JsonArray();
                    values.forEach(array::add);
                    columns.add(key, array);
                });
        json.add(PARAMETERS, params);
        json.add(INPUT_COLUMNS, columns);
        return json;
    }

    /** Reads remembered values; anything malformed is ignored. */
    public static FormValues fromJson(JsonObject json) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        Map<String, List<String>> columns = new LinkedHashMap<>();
        if (json != null) {
            JsonElement p = json.get(PARAMETERS);
            if (p != null && p.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : p.getAsJsonObject().entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        params.put(e.getKey(), List.of(e.getValue().getAsString()));
                    }
                }
            }
            JsonElement c = json.get(INPUT_COLUMNS);
            if (c != null && c.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : c.getAsJsonObject().entrySet()) {
                    List<String> chosen = new ArrayList<>();
                    if (e.getValue().isJsonArray()) {
                        for (JsonElement v : e.getValue().getAsJsonArray()) {
                            if (v.isJsonPrimitive()) {
                                chosen.add(v.getAsString());
                            }
                        }
                    } else if (e.getValue().isJsonPrimitive()) {
                        chosen.add(e.getValue().getAsString());
                    }
                    columns.put(e.getKey(), chosen);
                }
            }
        }
        return new FormValues(params, columns);
    }

    private static Map<String, List<String>> copy(Map<String, List<String>> map) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        if (map != null) {
            map.forEach(
                    (key, values) ->
                            copy.put(key, values == null ? List.of() : List.copyOf(values)));
        }
        return Collections.unmodifiableMap(copy);
    }
}
