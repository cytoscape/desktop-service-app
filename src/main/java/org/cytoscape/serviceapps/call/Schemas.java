package org.cytoscape.serviceapps.call;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Small builders for the JSON Schemas {@code serviceapps describe} returns. */
final class Schemas {

    static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";

    private Schemas() {}

    static JsonObject property(String type, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", type);
        if (description != null && !description.isBlank()) {
            property.addProperty("description", description);
        }
        return property;
    }

    static JsonObject object(String description, JsonObject properties, List<String> required) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        if (description != null) {
            schema.addProperty("description", description);
        }
        schema.add("properties", properties);
        schema.add("required", strings(required));
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    /** A top-level schema: the object schema with its dialect declared first. */
    static JsonObject root(String description, JsonObject properties, List<String> required) {
        JsonObject schema = new JsonObject();
        schema.addProperty("$schema", DIALECT);
        object(description, properties, required)
                .entrySet()
                .forEach(e -> schema.add(e.getKey(), e.getValue()));
        return schema;
    }

    static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
