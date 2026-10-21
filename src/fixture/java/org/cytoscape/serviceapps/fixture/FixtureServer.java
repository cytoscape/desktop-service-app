package org.cytoscape.serviceapps.fixture;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * The hello-world service app: a local server that implements the six spec endpoints for every
 * scenario in {@link ScenarioCatalog}.
 *
 * <pre>
 * GET    /{scenario}                metadata
 * GET    /{scenario}/status         service status
 * POST   /{scenario}                submit a task: 202 {"id"}
 * GET    /{scenario}/{id}/status    task progress, result null
 * GET    /{scenario}/{id}           task status and result
 * DELETE /{scenario}/{id}           delete the task: 204
 * </pre>
 */
public class FixtureServer implements AutoCloseable {

    /** One request the server received. */
    public record RecordedRequest(String method, String path, String body) {}

    private static final class TaskState {
        final Scenario scenario;
        int polls;

        TaskState(Scenario scenario) {
            this.scenario = scenario;
        }
    }

    private final Map<String, Scenario> scenarios;
    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final HttpServer server;

    /** Starts on {@code port}; 0 picks a free port. */
    public static FixtureServer start(int port) throws IOException {
        return new FixtureServer(port, ScenarioCatalog.all());
    }

    FixtureServer(int port, Map<String, Scenario> scenarios) throws IOException {
        this.scenarios = scenarios;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    /** The URL a scenario is served at, as a service app would be installed. */
    public String url(String scenario) {
        ScenarioCatalog.get(scenario);
        return baseUrl() + "/" + scenario;
    }

    /** Every request received, in order. */
    public List<RecordedRequest> requests() {
        return new ArrayList<>(requests);
    }

    public void clearRequests() {
        requests.clear();
    }

    /** IDs of tasks that were submitted and not yet deleted. */
    public List<String> liveTaskIds() {
        return new ArrayList<>(tasks.keySet());
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String body = readBody(exchange.getRequestBody());
            requests.add(new RecordedRequest(method, path, body));

            String[] parts = path.replaceAll("^/+|/+$", "").split("/");
            Scenario scenario = parts[0].isEmpty() ? null : scenarios.get(parts[0]);
            if (scenario == null) {
                reply(exchange, 404, message("no service app at " + path));
                return;
            }
            pause(scenario.behavior().latencyMs());

            if (parts.length == 1 && method.equals("GET")) {
                reply(exchange, 200, scenario.metadata());
            } else if (parts.length == 1 && method.equals("POST")) {
                submit(exchange, scenario);
            } else if (parts.length == 2 && parts[1].equals("status") && method.equals("GET")) {
                reply(exchange, 200, serviceStatus(scenario));
            } else if (parts.length == 3 && parts[2].equals("status") && method.equals("GET")) {
                taskReply(exchange, parts[1], true);
            } else if (parts.length == 2 && method.equals("GET")) {
                taskReply(exchange, parts[1], false);
            } else if (parts.length == 2 && method.equals("DELETE")) {
                if (tasks.remove(parts[1]) == null) {
                    reply(exchange, 404, message("no task " + parts[1]));
                } else {
                    exchange.sendResponseHeaders(204, -1);
                }
            } else {
                reply(exchange, 405, message(method + " " + path + " is not supported"));
            }
        }
    }

    private void submit(HttpExchange exchange, Scenario scenario) throws IOException {
        int status = scenario.behavior().submitStatus();
        if (status != 202) {
            reply(exchange, status, message("fixture refused the task"));
            return;
        }
        String id = UUID.randomUUID().toString();
        tasks.put(id, new TaskState(scenario));
        JsonObject reply = new JsonObject();
        reply.addProperty("id", id);
        reply(exchange, 202, reply);
    }

    /** Status polls advance the task one progress step each; result fetches don't. */
    private void taskReply(HttpExchange exchange, String id, boolean statusOnly)
            throws IOException {
        TaskState task = tasks.get(id);
        if (task == null) {
            reply(exchange, 404, message("no task " + id));
            return;
        }
        Scenario.Behavior behavior = task.scenario.behavior();
        int progress;
        synchronized (task) {
            if (statusOnly) {
                task.polls++;
            }
            List<Integer> steps = behavior.progressSteps();
            int index = Math.min(Math.max(task.polls - 1, 0), steps.size() - 1);
            progress = steps.get(index);
        }
        boolean finished = !behavior.neverFinishes() && progress >= 100;
        if (behavior.neverFinishes()) {
            progress = Math.min(progress, 99);
        }

        JsonObject reply = new JsonObject();
        reply.addProperty("id", id);
        reply.addProperty("status", finished ? behavior.finalStatus() : "processing");
        reply.addProperty("message", finished ? behavior.finalMessage() : "working");
        reply.addProperty("progress", progress);
        if (!statusOnly && finished && "complete".equals(behavior.finalStatus())) {
            reply.add("result", task.scenario.result());
        } else {
            reply.add("result", JsonNull.INSTANCE);
        }
        reply(exchange, 200, reply);
    }

    private JsonObject serviceStatus(Scenario scenario) {
        JsonObject status = new JsonObject();
        status.addProperty("status", "ok");
        status.addProperty("version", scenario.appVersion());
        status.addProperty(
                "numberOfRunningTasks",
                tasks.values().stream().filter(t -> t.scenario == scenario).count());
        return status;
    }

    private static JsonObject message(String text) {
        JsonObject message = new JsonObject();
        message.addProperty("message", text);
        return message;
    }

    private static void reply(HttpExchange exchange, int status, JsonObject body)
            throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String readBody(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void pause(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
