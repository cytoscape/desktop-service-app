package org.cytoscape.serviceapps.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

/**
 * The one Gson instance the app uses to read and write service app JSON.
 *
 * <p>Unknown fields are ignored, as the spec requires: a service may add attributes a client does
 * not know about.
 */
public final class Json {

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /**
     * For command replies, where a null field ({@code "httpStatus": null}) is part of the answer.
     */
    private static final Gson GSON_WITH_NULLS =
            new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private Json() {}

    /**
     * Parses {@code json} as {@code type}.
     *
     * @throws JsonParseException if the text is not valid JSON for that type, or is empty
     */
    public static <T> T parse(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            throw new JsonParseException("expected a JSON body, got none");
        }
        T value = GSON.fromJson(json, type);
        if (value == null) {
            throw new JsonParseException("expected a JSON body, got 'null'");
        }
        return value;
    }

    public static String write(Object value) {
        return GSON.toJson(value);
    }

    /** Writes {@code value}, keeping null fields. */
    public static String writeWithNulls(Object value) {
        return GSON_WITH_NULLS.toJson(value);
    }

    public static JsonElement tree(Object value) {
        return GSON.toJsonTree(value);
    }
}
