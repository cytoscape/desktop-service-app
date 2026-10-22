package org.cytoscape.serviceapps.model;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

/**
 * A task's state, returned by {@code GET /{id}/status} (with {@code result} null) and {@code GET
 * /{id}} (with the result).
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/Task-Result-Response">Task Result
 *     Response</a>
 */
public class TaskStatus {

    public static final String SUBMITTED = "submitted";
    public static final String PROCESSING = "processing";
    public static final String COMPLETE = "complete";
    public static final String FAILED = "failed";

    public String id;
    public String status;
    public String message;

    /** 0 to 100. */
    public Double progress;

    /** An ordered list of result actions; null on the status endpoint. */
    public JsonElement result;

    public boolean isComplete() {
        return COMPLETE.equals(status);
    }

    public boolean isFailed() {
        return FAILED.equals(status);
    }

    /**
     * The result as an ordered list of actions.
     *
     * @throws JsonParseException if {@code result} is present but not a list of actions, or an
     *     entry is not an action object with an {@code action} name
     */
    public List<ResultAction> actions() {
        if (result == null || result.isJsonNull()) {
            return List.of();
        }
        if (!result.isJsonArray()) {
            throw new JsonParseException("task result is not a list of actions");
        }
        JsonArray array = result.getAsJsonArray();
        List<ResultAction> actions = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element == null || !element.isJsonObject()) {
                throw new JsonParseException(
                        "task result entry " + (i + 1) + " is not an action: " + element);
            }
            ResultAction action = Json.GSON.fromJson(element, ResultAction.class);
            if (action.action == null || action.action.isBlank()) {
                throw new JsonParseException(
                        "task result entry " + (i + 1) + " names no action: " + element);
            }
            actions.add(action);
        }
        return actions;
    }
}
