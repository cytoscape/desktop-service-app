package org.cytoscape.serviceapps.desktop;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Calls Cytoscape's CyREST commands the two ways clients do.
 *
 * <ul>
 *   <li>{@link #get}: {@code GET /v1/commands/...?args}, as the App Store page does. CyREST wraps
 *       the text reply in HTML paragraphs.
 *   <li>{@link #post}: {@code POST} with a JSON body, as automation clients do. CyREST replies
 *       {@code {"data": ..., "errors": [...]}}.
 * </ul>
 */
public final class CyRest {

    /** A command's JSON reply. */
    public record Reply(JsonElement data, JsonElement errors) {
        public boolean ok() {
            return errors == null || !errors.isJsonArray() || errors.getAsJsonArray().isEmpty();
        }

        @Override
        public String toString() {
            return "data=" + data + " errors=" + errors;
        }
    }

    private final String baseUrl;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public CyRest(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** Whether CyREST answers {@code GET /v1}. */
    public boolean isUp() {
        try {
            return send(HttpRequest.newBuilder(URI.create(baseUrl + "/v1")).GET())
                    .contains("apiVersion");
        } catch (IOException e) {
            return false;
        }
    }

    /** Whether the app's commands are registered. */
    public boolean hasServiceApps() {
        try {
            return post("serviceapps", "list", Map.of()).ok();
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** {@code GET} any CyREST path, as plain text with HTML wrapping removed. */
    public String text(String path) throws IOException {
        return stripHtml(send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET()));
    }

    /**
     * {@code GET /v1/commands/{namespace}/{command}?args}; returns the reply with CyREST's HTML
     * paragraph wrapping removed.
     */
    public String get(String namespace, String command, Map<String, String> args)
            throws IOException {
        String query =
                args.entrySet().stream()
                        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                        .collect(Collectors.joining("&"));
        URI uri = URI.create(commandUrl(namespace, command) + (query.isEmpty() ? "" : "?" + query));
        return stripHtml(send(HttpRequest.newBuilder(uri).GET()));
    }

    /** {@code POST /v1/commands/{namespace}/{command}} with the arguments as a JSON object. */
    public Reply post(String namespace, String command, Map<String, String> args)
            throws IOException {
        JsonObject body = new JsonObject();
        new LinkedHashMap<>(args).forEach(body::addProperty);
        String reply =
                send(
                        HttpRequest.newBuilder(URI.create(commandUrl(namespace, command)))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
        JsonObject json = JsonParser.parseString(reply).getAsJsonObject();
        return new Reply(json.get("data"), json.get("errors"));
    }

    private String commandUrl(String namespace, String command) {
        return baseUrl + "/v1/commands/" + namespace + "/" + encode(command).replace("+", "%20");
    }

    private String send(HttpRequest.Builder request) throws IOException {
        try {
            return http.send(
                            request.timeout(Duration.ofSeconds(60)).build(),
                            HttpResponse.BodyHandlers.ofString())
                    .body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    /** CyREST's GET replies wrap each output line in a {@code <p>}; keep only the text. */
    static String stripHtml(String reply) {
        return reply.replaceAll("<[^>]+>", "").strip();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
