package org.cytoscape.serviceapps.command;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.model.TaskStatus;
import org.cytoscape.serviceapps.run.ServiceAppRunRegistry;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;

/**
 * {@code serviceapps task status task_id=}: the state of a run started by {@code task call}, in the
 * spec's Task Status model.
 */
public class TaskStatusCommandTask extends JsonResultTask {

    public static final String NAME = "task status";
    public static final String DESCRIPTION =
            "Get the status of a service app run started by task call";
    public static final String LONG_DESCRIPTION =
            "Returns {progress, message, status, result: null}, the service app spec's Task Status"
                    + " model, for a task_id from serviceapps task call.\n\n"
                    + "- status: submitted, processing, complete or failed. complete is reported"
                    + " only after the result has been applied in Desktop.\n"
                    + "- progress: 0 to 100.\n"
                    + "- message: progress from the service; on complete, what was applied and any"
                    + " notes; on failed, why. A failure of the service starts with 'Service task"
                    + " failed:', a failure while applying the result with 'Rendering failed'.\n\n"
                    + "Desktop keeps the last 100 runs; an unknown or expired task_id is an error.";
    public static final String EXAMPLE_JSON =
            "{\"progress\":100,\"message\":\"Applied updateLayouts\",\"status\":\"complete\","
                    + "\"result\":null}";

    @Tunable(
            description = "Task ID from task call",
            longDescription = "The task_id that serviceapps task call returned.",
            exampleStringValue = "6f1c2b2e-6a8e-4f0b-9a55-2b0c1f9d7e41",
            required = true,
            context = "nogui")
    public String task_id;

    private final ServiceAppRunRegistry runs;

    public TaskStatusCommandTask(ServiceAppRunRegistry runs) {
        this.runs = runs;
    }

    @Override
    public void run(TaskMonitor monitor) {
        String id = required(task_id, "task_id");
        TaskStatus status =
                runs.status(id)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "no task with task_id '"
                                                        + id
                                                        + "': it is unknown, or older than the last"
                                                        + " "
                                                        + ServiceAppRunRegistry.KEPT_RUNS
                                                        + " runs"));
        JsonObject json = new JsonObject();
        json.addProperty("progress", status.progress == null ? 0 : status.progress);
        json.addProperty("message", status.message == null ? "" : status.message);
        json.addProperty("status", status.status);
        json.add("result", JsonNull.INSTANCE);
        setResult(json, Json.writeWithNulls(json));
    }
}
