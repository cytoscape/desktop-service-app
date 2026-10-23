package org.cytoscape.serviceapps.fixture;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * One fixture service app: the metadata it serves, the result its tasks return, and how its tasks
 * behave.
 *
 * <p>Each scenario is served at {@code <server>/<name>}, so one server hosts the whole catalogue.
 */
public record Scenario(String name, JsonObject metadata, JsonArray result, Behavior behavior) {

    /**
     * How a submitted task behaves.
     *
     * @param progressSteps the progress reported on successive status polls; the last step repeats
     * @param finalStatus {@code complete} or {@code failed}, reported once progress reaches 100
     * @param finalMessage the message reported with the final status
     * @param neverFinishes keep reporting {@code processing} below 100 forever
     * @param submitStatus the HTTP status for {@code POST /}; anything but 202 is an error reply
     * @param latencyMs delay before every reply from this scenario
     */
    public record Behavior(
            List<Integer> progressSteps,
            String finalStatus,
            String finalMessage,
            boolean neverFinishes,
            int submitStatus,
            long latencyMs) {

        public static final Behavior NORMAL =
                new Behavior(List.of(0, 50, 100), "complete", "", false, 202, 0);

        public Behavior withProgressSteps(List<Integer> steps) {
            return new Behavior(
                    steps, finalStatus, finalMessage, neverFinishes, submitStatus, latencyMs);
        }

        public Behavior withFinal(String status, String message) {
            return new Behavior(
                    progressSteps, status, message, neverFinishes, submitStatus, latencyMs);
        }

        public Behavior neverFinishing() {
            return new Behavior(
                    progressSteps, finalStatus, finalMessage, true, submitStatus, latencyMs);
        }

        public Behavior withSubmitStatus(int status) {
            return new Behavior(
                    progressSteps, finalStatus, finalMessage, neverFinishes, status, latencyMs);
        }

        public Behavior withLatency(long ms) {
            return new Behavior(
                    progressSteps, finalStatus, finalMessage, neverFinishes, submitStatus, ms);
        }
    }

    /** The service app name in the metadata, which {@code serviceapps install} installs under. */
    public String appName() {
        return metadata.get("name").getAsString();
    }

    /** The version in the metadata. */
    public String appVersion() {
        return metadata.get("version").getAsString();
    }
}
