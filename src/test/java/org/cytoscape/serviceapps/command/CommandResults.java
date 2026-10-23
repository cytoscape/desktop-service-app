package org.cytoscape.serviceapps.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.work.json.JSONResult;

/** Reads a command's JSON result, as CyREST would return it. */
final class CommandResults {

    private CommandResults() {}

    static JsonObject json(JsonResultTask task) {
        return JsonParser.parseString(task.getResults(JSONResult.class).getJSON())
                .getAsJsonObject();
    }
}
