package org.cytoscape.serviceapps.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/** Reads metadata fields that Cytoscape Web accepts as either one string or a list of strings. */
final class MetadataLists {

    private MetadataLists() {}

    /**
     * {@code columnTypeFilter}: a string, a list of strings, or null. Anything else is malformed
     * metadata, as in Cytoscape Web. Written back in the form it most likely came in: null when
     * empty, a string for one filter, otherwise a list.
     */
    static final class StringOrList extends TypeAdapter<List<String>> {

        @Override
        public List<String> read(JsonReader in) throws IOException {
            JsonElement value = JsonParser.parseReader(in);
            List<String> filters = new ArrayList<>();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                filters.add(value.getAsString());
            } else if (value.isJsonArray()) {
                for (JsonElement item : value.getAsJsonArray()) {
                    if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                        throw new JsonParseException(
                                "columnTypeFilter must be a string or a list of strings");
                    }
                    filters.add(item.getAsString());
                }
            } else if (!value.isJsonNull()) {
                throw new JsonParseException(
                        "columnTypeFilter must be a string or a list of strings");
            }
            filters.removeIf(String::isBlank);
            return filters;
        }

        @Override
        public void write(JsonWriter out, List<String> value) throws IOException {
            if (value == null || value.isEmpty()) {
                out.nullValue();
            } else if (value.size() == 1) {
                out.value(value.get(0));
            } else {
                out.beginArray();
                for (String filter : value) {
                    out.value(filter);
                }
                out.endArray();
            }
        }
    }

    /**
     * {@code groups}: a list of non-blank strings. Anything else is read as no groups, as in
     * Cytoscape Web.
     */
    static final class Groups extends TypeAdapter<List<String>> {

        @Override
        public List<String> read(JsonReader in) throws IOException {
            JsonElement value = JsonParser.parseReader(in);
            if (!value.isJsonArray()) {
                return List.of();
            }
            List<String> groups = new ArrayList<>();
            for (JsonElement item : value.getAsJsonArray()) {
                if (!item.isJsonPrimitive()
                        || !item.getAsJsonPrimitive().isString()
                        || item.getAsString().isBlank()) {
                    return List.of();
                }
                groups.add(item.getAsString());
            }
            return groups;
        }

        @Override
        public void write(JsonWriter out, List<String> value) throws IOException {
            if (value == null || value.isEmpty()) {
                out.nullValue();
                return;
            }
            out.beginArray();
            for (String group : value) {
                out.value(group);
            }
            out.endArray();
        }
    }
}
