package org.cytoscape.serviceapps.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;

/**
 * The body of {@code POST /}.
 *
 * <p>{@code algorithm} is not in the spec's request body, but Cytoscape Web sends it and existing
 * hosts expect it, so it is sent too.
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/Task-Request">Task Request</a>
 */
public class TaskRequest {

    public String algorithm;
    public JsonElement data;
    public Map<String, String> parameters = new LinkedHashMap<>();

    public TaskRequest() {}

    public TaskRequest(String algorithm, JsonElement data, Map<String, String> parameters) {
        this.algorithm = algorithm;
        this.data = data;
        this.parameters = new LinkedHashMap<>(parameters);
    }
}
