package org.cytoscape.serviceapps.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.model.ServiceStatus;
import org.cytoscape.serviceapps.model.TaskRequest;
import org.cytoscape.serviceapps.model.TaskStatus;

/**
 * Calls the six endpoints of one service app.
 *
 * @see <a
 *     href="https://github.com/cytoscape/cytoscape-web/wiki/Specification-for-Service-App-in-Cytoscape-Web-(draft-v2)">Specification
 *     for Service App, draft v2</a>
 */
public class ServiceAppClient {

    /** Longest stretch of a non-JSON error body quoted in an exception message. */
    private static final int MAX_QUOTED_BODY = 200;

    private final String baseUrl;
    private final HttpTransport transport;

    /**
     * @param baseUrl the service app's URL, as installed; a trailing slash is ignored
     * @throws IllegalArgumentException if it is not an http or https URL
     */
    public ServiceAppClient(String baseUrl, HttpTransport transport) {
        this.baseUrl = normalize(baseUrl);
        this.transport = transport;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** {@code GET /}: the service's metadata. */
    public ServiceMetadata getMetadata() {
        return parse(call("GET", baseUrl, null), ServiceMetadata.class, "metadata");
    }

    /** {@code GET /status}: the service's health. */
    public ServiceStatus getStatus() {
        HttpTransport.Response response = call("GET", baseUrl + "/status", null);
        ServiceStatus status = parse(response, ServiceStatus.class, "status");
        status.httpStatus = response.status();
        return status;
    }

    /**
     * {@code POST /}: starts a task.
     *
     * @return the task ID the service assigned
     */
    public String submit(TaskRequest request) {
        HttpTransport.Response response = call("POST", baseUrl, Json.write(request));
        JsonObject reply = parse(response, JsonObject.class, "task submission");
        JsonElement id = reply.get("id");
        if (id == null || id.isJsonNull() || id.getAsString().isBlank()) {
            throw new ServiceAppException(
                    response.status(), "service accepted the task but returned no task id");
        }
        return id.getAsString();
    }

    /** {@code GET /{id}/status}: a task's progress; {@code result} is null. */
    public TaskStatus getTaskStatus(String taskId) {
        return parse(
                call("GET", taskUrl(taskId) + "/status", null), TaskStatus.class, "task status");
    }

    /** {@code GET /{id}}: a task's status and result. */
    public TaskStatus getTaskResult(String taskId) {
        return parse(call("GET", taskUrl(taskId), null), TaskStatus.class, "task result");
    }

    /** {@code DELETE /{id}}: stops the task if it is running, and deletes it. */
    public void deleteTask(String taskId) {
        call("DELETE", taskUrl(taskId), null);
    }

    private String taskUrl(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("task id is required");
        }
        // URLEncoder encodes for forms; a path segment needs %20, not +, for a space.
        return baseUrl
                + "/"
                + URLEncoder.encode(taskId, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private HttpTransport.Response call(String method, String url, String body) {
        HttpTransport.Response response;
        try {
            response = transport.send(method, URI.create(url), body);
        } catch (IOException e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new ServiceAppException(
                    ServiceAppException.NO_RESPONSE, "could not reach " + url + ": " + reason, e);
        }
        if (!response.isSuccess()) {
            throw new ServiceAppException(
                    response.status(),
                    method
                            + " "
                            + url
                            + " returned HTTP "
                            + response.status()
                            + errorDetail(response.body()));
        }
        return response;
    }

    private static <T> T parse(HttpTransport.Response response, Class<T> type, String what) {
        try {
            return Json.parse(response.body(), type);
        } catch (JsonParseException | IllegalStateException e) {
            throw new ServiceAppException(
                    response.status(),
                    "service returned an invalid " + what + ": " + e.getMessage(),
                    e);
        }
    }

    /** The service's own {@code message} when it sent one, otherwise the start of the body. */
    private static String errorDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (parsed.isJsonObject()) {
                JsonElement message = parsed.getAsJsonObject().get("message");
                if (message != null && !message.isJsonNull()) {
                    return ": " + message.getAsString();
                }
            }
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            // not JSON; quote the text instead
        }
        String text = body.strip();
        return ": "
                + (text.length() > MAX_QUOTED_BODY
                        ? text.substring(0, MAX_QUOTED_BODY) + "..."
                        : text);
    }

    static String normalize(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("service app URL is required");
        }
        String trimmed = url.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("not a valid URL: " + url, e);
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("not an http or https URL: " + url);
        }
        return trimmed;
    }
}
