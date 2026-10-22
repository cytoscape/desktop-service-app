package org.cytoscape.serviceapps.command;

import java.util.List;

import com.google.gson.JsonElement;

import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.json.JSONResult;

/**
 * A command task whose result is JSON, plus a text form for the Command Line and for callers that
 * read CyREST's text reply.
 */
abstract class JsonResultTask extends AbstractTask implements ObservableTask {

    private JsonElement json;
    private String text;

    /** Called by subclasses at the end of {@code run}. */
    protected void setResult(JsonElement json, String text) {
        this.json = json;
        this.text = text;
    }

    @Override
    public List<Class<?>> getResultClasses() {
        return List.of(String.class, JSONResult.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R getResults(Class<? extends R> type) {
        if (json == null) {
            return null;
        }
        if (JSONResult.class.equals(type)) {
            String body = Json.writeWithNulls(json);
            JSONResult result = () -> body;
            return (R) result;
        }
        if (String.class.equals(type)) {
            return (R) text;
        }
        return null;
    }

    static String required(String value, String argument) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(argument + "= is required");
        }
        return value.strip();
    }
}
