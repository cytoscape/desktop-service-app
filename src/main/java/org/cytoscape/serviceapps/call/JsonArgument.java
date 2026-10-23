package org.cytoscape.serviceapps.call;

import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/**
 * Reads a command argument that holds a JSON object as text, such as {@code serviceInput}.
 *
 * <p>Desktop passes command arguments as text. A client that sends an object instead of a JSON
 * string gets Java's {@code {key=value}} form here, as the MCP Desktop app does; that is recognised
 * and explained.
 */
final class JsonArgument {

    /** Java's {@code Map.toString()} form: braces around {@code key=value} pairs. */
    private static final Pattern MAP_TO_STRING =
            Pattern.compile("\\{\\s*[^\"{}:]+=.*}", Pattern.DOTALL); // matches() is a full match

    private static final TypeAdapter<JsonElement> ELEMENTS =
            new Gson().getAdapter(JsonElement.class);

    private JsonArgument() {}

    /** Strict JSON: Gson's default lenient parsing would accept {@code {key=value}}. */
    private static JsonElement strict(String text) throws IOException {
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setStrictness(Strictness.STRICT);
        JsonElement element = ELEMENTS.read(reader);
        if (reader.peek() != JsonToken.END_DOCUMENT) {
            throw new JsonParseException("text after the JSON value");
        }
        return element;
    }

    /**
     * @return the object, or an empty object when {@code value} is null or blank
     * @throws IllegalArgumentException with a message naming the argument
     */
    static JsonObject object(String value, String argument) {
        if (value == null || value.isBlank()) {
            return new JsonObject();
        }
        String text = value.strip();
        JsonElement parsed;
        try {
            parsed = strict(text);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            if (MAP_TO_STRING.matcher(text).matches()) {
                throw new IllegalArgumentException(
                        argument
                                + " must be passed as a JSON string, such as "
                                + "'{\"parameters\":{\"Resolution\":0.5}}'. It arrived as "
                                + text
                                + ", which is what an object becomes when it is passed instead"
                                + " of a string.");
            }
            throw new IllegalArgumentException(argument + " is not valid JSON: " + text);
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException(argument + " must be a JSON object, not " + text);
        }
        return parsed.getAsJsonObject();
    }
}
