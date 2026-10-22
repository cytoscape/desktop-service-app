package org.cytoscape.serviceapps.contract;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.desktop.CyRest;
import org.cytoscape.serviceapps.desktop.DesktopContainer;
import org.cytoscape.serviceapps.desktop.DesktopExtension;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.ScenarioCatalog;
import org.cytoscape.serviceapps.testsupport.ContractNetwork;

/**
 * Reaches Desktop the way an agent does: {@code serviceapps task call} over CyREST, then {@code
 * task status} until the run finishes, against real Cytoscape in Docker with the real CX Support
 * app.
 *
 * <p>The contract networks are CX2 files with the fixture's element IDs, loaded through CX Support,
 * which keeps those IDs. Each node and edge also carries its ID in a visible {@code cxid} column,
 * because CyREST can't read the hidden ID column.
 *
 * <p>With {@code omitUiInput}, every run leaves {@code uiInput} out and makes the target the active
 * network instead, so the result goes to it and every one of its views.
 */
public class DockerTaskCallHarness implements ContractHarness {

    private static final String TARGET_FILE = "/tmp/contract-target.cx2";
    private static final String OTHER_FILE = "/tmp/contract-other.cx2";
    private static final String OPENED_URLS = "/tmp/opened-urls";
    private static final long RUN_TIMEOUT_MS = 60_000;

    private final DesktopContainer desktop;
    private final FixtureServer fixture;
    private final CyRest cyrest;
    private final boolean omitUiInput;
    private final Set<String> installed = new HashSet<>();

    private long target;
    private long other;
    private final List<Long> targetViews = new ArrayList<>();

    public DockerTaskCallHarness(
            DesktopContainer desktop, FixtureServer fixture, boolean omitUiInput) {
        this.desktop = desktop;
        this.fixture = fixture;
        this.cyrest = desktop.cyrest();
        this.omitUiInput = omitUiInput;
        copyContractNetworks();
    }

    private void copyContractNetworks() {
        try {
            Path dir = Files.createTempDirectory("contract-networks");
            Path targetFile = Files.writeString(dir.resolve("target.cx2"), cx2("target"));
            Path otherFile = Files.writeString(dir.resolve("other.cx2"), cx2("other"));
            desktop.copyIn(targetFile, TARGET_FILE);
            desktop.copyIn(otherFile, OTHER_FILE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A CX2 network with {@link ContractNetwork}'s element IDs, names and a {@code cxid} column.
     */
    static String cx2(String name) {
        JsonArray nodes = new JsonArray();
        for (long id : ContractNetwork.NODE_IDS) {
            JsonObject node = new JsonObject();
            node.addProperty("id", id);
            JsonObject values = new JsonObject();
            values.addProperty("name", "n" + id);
            values.addProperty("cxid", id);
            node.add("v", values);
            node.addProperty("x", 0.0);
            node.addProperty("y", 0.0);
            nodes.add(node);
        }
        JsonArray edges = new JsonArray();
        edges.add(edge(ContractNetwork.EDGE_3, 0, 1));
        edges.add(edge(ContractNetwork.EDGE_6, 7, 15));
        String declarations =
                "{\"attributeDeclarations\":[{\"networkAttributes\":{\"name\":{\"d\":\"string\"}},"
                        + "\"nodes\":{\"name\":{\"d\":\"string\"},\"cxid\":{\"d\":\"long\"}},"
                        + "\"edges\":{\"cxid\":{\"d\":\"long\"}}}]}";
        return "[{\"CXVersion\":\"2.0\",\"hasFragments\":false},"
                + declarations
                + ",{\"networkAttributes\":[{\"name\":\""
                + name
                + "\"}]},{\"nodes\":"
                + nodes
                + "},{\"edges\":"
                + edges
                + "},{\"status\":[{\"error\":\"\",\"success\":true}]}]";
    }

    private static JsonObject edge(long id, long source, long target) {
        JsonObject edge = new JsonObject();
        edge.addProperty("id", id);
        edge.addProperty("s", source);
        edge.addProperty("t", target);
        JsonObject values = new JsonObject();
        values.addProperty("cxid", id);
        edge.add("v", values);
        return edge;
    }

    @Override
    public boolean supportsViewTarget() {
        return !omitUiInput;
    }

    @Override
    public void reset() {
        desktop.exec("rm", "-f", OPENED_URLS);
        loadNetworks();
    }

    private void loadNetworks() {
        rest("DELETE", "/v1/networks", null);
        other = load(OTHER_FILE)[0];
        long[] loaded = load(TARGET_FILE);
        target = loaded[0];
        targetViews.clear();
        targetViews.add(loaded[1]);
        makeCurrent(target);
    }

    /**
     * @return {network SUID, view SUID}
     */
    private long[] load(String file) {
        CyRest.Reply reply = command("network", "load file", Map.of("file", file));
        JsonObject data = reply.data().getAsJsonObject();
        return new long[] {
            data.getAsJsonArray("networks").get(0).getAsLong(),
            data.getAsJsonArray("views").get(0).getAsLong()
        };
    }

    private void makeCurrent(long network) {
        JsonObject body = new JsonObject();
        body.addProperty("networkSUID", network);
        rest("PUT", "/v1/networks/currentNetwork", body);
    }

    @Override
    public void select(long... nodeIds) {
        Map<Long, Long> suids = suids("defaultnode");
        JsonArray rows = new JsonArray();
        for (long id : nodeIds) {
            JsonObject row = new JsonObject();
            row.addProperty("SUID", suids.get(id));
            row.addProperty("selected", true);
            rows.add(row);
        }
        updateRows(target, "defaultnode", rows);
    }

    @Override
    public void addTargetView() {
        JsonElement reply = rest("POST", "/v1/networks/" + target + "/views", null);
        targetViews.add(reply.getAsJsonObject().get("networkViewSUID").getAsLong());
    }

    @Override
    public void addNodeColumn(String name, Class<?> type) {
        createColumn(target, "defaultnode", name, type.getSimpleName());
    }

    @Override
    public void setNetworkAttribute(String name, String value) {
        if (!columnTypes(target, "defaultnetwork").containsKey(name)) {
            createColumn(target, "defaultnetwork", name, "String");
        }
        JsonObject row = new JsonObject();
        row.addProperty("SUID", target);
        row.addProperty(name, value);
        JsonArray rows = new JsonArray();
        rows.add(row);
        updateRows(target, "defaultnetwork", rows);
    }

    private void createColumn(long network, String table, String name, String type) {
        JsonObject column = new JsonObject();
        column.addProperty("name", name);
        column.addProperty("type", type);
        rest("POST", "/v1/networks/" + network + "/tables/" + table + "/columns", column);
    }

    private void updateRows(long network, String table, JsonArray rows) {
        JsonObject body = new JsonObject();
        body.addProperty("key", "SUID");
        body.addProperty("dataKey", "SUID");
        body.add("data", rows);
        rest("PUT", "/v1/networks/" + network + "/tables/" + table, body);
    }

    @Override
    public Outcome run(String scenario, Ui ui) {
        install(scenario);
        Map<String, String> args = new LinkedHashMap<>();
        args.put("app", ScenarioCatalog.get(scenario).appName());
        boolean noNetwork = ui == Ui.NO_NETWORK;
        if (noNetwork) {
            rest("DELETE", "/v1/networks", null);
        } else if (omitUiInput) {
            makeCurrent(target);
        } else if (ui == Ui.CURRENT_VIEW) {
            args.put(
                    "uiInput",
                    "{\"networkId\":" + target + ",\"networkViewId\":" + targetViews.get(0) + "}");
        } else {
            args.put("uiInput", "{\"networkId\":" + target + "}");
        }
        try {
            CyRest.Reply reply = cyrest.post("serviceapps", "task call", args);
            if (!reply.ok()) {
                return new Outcome(false, errorMessage(reply));
            }
            String taskId = reply.data().getAsJsonObject().get("task_id").getAsString();
            return await(taskId);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (noNetwork) {
                // The contract's second network must come back unchanged, so reload both.
                loadNetworks();
            }
        }
    }

    private Outcome await(String taskId) throws IOException {
        long deadline = System.currentTimeMillis() + RUN_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            CyRest.Reply reply =
                    cyrest.post("serviceapps", "task status", Map.of("task_id", taskId));
            if (!reply.ok()) {
                throw new IllegalStateException("task status failed: " + reply);
            }
            JsonObject status = reply.data().getAsJsonObject();
            String state = status.get("status").getAsString();
            if (state.equals("complete") || state.equals("failed")) {
                return new Outcome(state.equals("complete"), status.get("message").getAsString());
            }
            sleep(200);
        }
        throw new IllegalStateException(
                "task " + taskId + " didn't finish in " + RUN_TIMEOUT_MS + " ms");
    }

    private void install(String scenario) {
        if (installed.contains(scenario)) {
            return;
        }
        command(
                "serviceapps",
                "install",
                Map.of(
                        "name", ScenarioCatalog.get(scenario).appName(),
                        "version", ScenarioCatalog.get(scenario).appVersion(),
                        "url", DesktopExtension.containerUrl(fixture, scenario)));
        installed.add(scenario);
    }

    // --- the target network ---

    @Override
    public long targetSuid() {
        return target;
    }

    /** The SUID of the target's view {@code index} (0 is the one it was loaded with). */
    public long targetViewSuid(int index) {
        return targetViews.get(index);
    }

    /** The second network's SUID. */
    public long otherSuidForTests() {
        return other;
    }

    /** Waits for a task call's run to finish; a rejected call is a failed outcome. */
    public Outcome await(CyRest.Reply reply) {
        if (!reply.ok()) {
            return new Outcome(false, errorMessage(reply));
        }
        try {
            return await(reply.data().getAsJsonObject().get("task_id").getAsString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Installs a fixture scenario's app, if this harness hasn't yet. */
    public void installScenario(String scenario) {
        install(scenario);
    }

    @Override
    public int nodeCount() {
        return rows(target, "defaultnode").size();
    }

    @Override
    public int edgeCount() {
        return rows(target, "defaultedge").size();
    }

    @Override
    public boolean hasNode(long id) {
        return row(target, "defaultnode", id) != null;
    }

    @Override
    public boolean hasEdge(long id) {
        return row(target, "defaultedge", id) != null;
    }

    @Override
    public boolean nodeSelected(long id) {
        return row(target, "defaultnode", id).get("selected").getAsBoolean();
    }

    @Override
    public boolean edgeSelected(long id) {
        return row(target, "defaultedge", id).get("selected").getAsBoolean();
    }

    @Override
    public double[] position(int view, long nodeId) {
        long node = row(target, "defaultnode", nodeId).get("SUID").getAsLong();
        long viewSuid = targetViews.get(view);
        return new double[] {
            visual(viewSuid, node, "NODE_X_LOCATION"),
            visual(viewSuid, node, "NODE_Y_LOCATION"),
            visual(viewSuid, node, "NODE_Z_LOCATION")
        };
    }

    private double visual(long view, long node, String property) {
        JsonElement reply =
                rest(
                        "GET",
                        "/v1/networks/"
                                + target
                                + "/views/"
                                + view
                                + "/nodes/"
                                + node
                                + "/"
                                + property,
                        null);
        return reply.getAsJsonObject().get("value").getAsDouble();
    }

    @Override
    public Object nodeValue(String column, long nodeId) {
        JsonObject row = row(target, "defaultnode", nodeId);
        String type = columnTypes(target, "defaultnode").get(column);
        if (type == null || !row.has(column) || row.get(column).isJsonNull()) {
            return null;
        }
        return value(row.get(column), type);
    }

    @Override
    public Class<?> nodeColumnType(String column) {
        String type = columnTypes(target, "defaultnode").get(column);
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "Integer" -> Integer.class;
            case "Long" -> Long.class;
            case "Double" -> Double.class;
            case "Boolean" -> Boolean.class;
            case "List" -> List.class;
            default -> String.class;
        };
    }

    @Override
    public Object networkValue(String column) {
        JsonObject row = rows(target, "defaultnetwork").get(0);
        return row.has(column) && !row.get(column).isJsonNull()
                ? row.get(column).getAsString()
                : null;
    }

    @Override
    public Map<String, Object> targetState() {
        return state(target, true);
    }

    @Override
    public Map<String, Object> otherState() {
        return state(other, false);
    }

    /** Every row value, keyed by element ID rather than SUID, plus positions of the target. */
    private Map<String, Object> state(long network, boolean withPositions) {
        Map<String, Object> state = new TreeMap<>();
        state.put("network", withoutSuid(rows(network, "defaultnetwork").get(0)));
        for (JsonObject row : rows(network, "defaultnode")) {
            String key = "node " + key(row);
            state.put(key, withoutSuid(row));
            if (withPositions && row.has("cxid") && !row.get("cxid").isJsonNull()) {
                for (int i = 0; i < targetViews.size(); i++) {
                    double[] p = position(i, row.get("cxid").getAsLong());
                    state.put(key + " view " + i, List.of(p[0], p[1], p[2]));
                }
            }
        }
        for (JsonObject row : rows(network, "defaultedge")) {
            state.put("edge " + key(row), withoutSuid(row));
        }
        return state;
    }

    private static String key(JsonObject row) {
        return row.has("cxid") && !row.get("cxid").isJsonNull()
                ? row.get("cxid").getAsString()
                : "new " + row.get("name");
    }

    private static String withoutSuid(JsonObject row) {
        JsonObject copy = row.deepCopy();
        copy.remove("SUID");
        return new TreeMap<>(copy.asMap()).toString();
    }

    // --- Desktop as a whole ---

    @Override
    public int collectionCount() {
        return rest("GET", "/v1/collections", null).getAsJsonArray().size();
    }

    private Long current() {
        JsonElement reply = rest("GET", "/v1/networks/currentNetwork", null);
        JsonElement suid = reply.getAsJsonObject().getAsJsonObject("data").get("networkSUID");
        return suid == null || suid.isJsonNull() ? null : suid.getAsLong();
    }

    @Override
    public String currentNetworkName() {
        Long current = current();
        return current == null
                ? null
                : rows(current, "defaultnetwork").get(0).get("name").getAsString();
    }

    @Override
    public int currentNetworkNodeCount() {
        Long current = current();
        return current == null ? 0 : rows(current, "defaultnode").size();
    }

    @Override
    public int currentNetworkEdgeCount() {
        Long current = current();
        return current == null ? 0 : rows(current, "defaultedge").size();
    }

    @Override
    public int currentNetworkViewCount() {
        Long current = current();
        return current == null
                ? 0
                : rest("GET", "/v1/networks/" + current + "/views", null).getAsJsonArray().size();
    }

    @Override
    public List<String> openedUrls() {
        String text = desktop.exec("sh", "-c", "cat " + OPENED_URLS + " 2>/dev/null || true");
        return text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    // --- CyREST ---

    /**
     * The table's rows for elements that are in the network.
     *
     * <p>Cytoscape Desktop can leave an orphan row behind after removing nodes or edges: another
     * Desktop thread (such as the search indexer) reads a row with {@code CyTable.getRow}, which
     * re-creates a row that was just deleted. Such a row holds only its SUID and {@code selected}
     * at its default, false, and its element isn't in the network. Those rows are left out, so
     * assertions see the network's logical state. A non-member row of any other shape is not a
     * known Desktop artifact and fails the test.
     */
    private List<JsonObject> rows(long network, String table) {
        List<JsonObject> rows = new ArrayList<>();
        rest("GET", "/v1/networks/" + network + "/tables/" + table + "/rows", null)
                .getAsJsonArray()
                .forEach(row -> rows.add(row.getAsJsonObject()));
        String elements = elementsOf(table);
        if (elements == null) {
            return rows;
        }
        Set<Long> members = new HashSet<>();
        rest("GET", "/v1/networks/" + network + "/" + elements, null)
                .getAsJsonArray()
                .forEach(suid -> members.add(suid.getAsLong()));
        List<JsonObject> logical = new ArrayList<>();
        for (JsonObject row : rows) {
            if (members.contains(row.get("SUID").getAsLong())) {
                logical.add(row);
            } else if (isOrphan(row)) {
                System.out.println("Left out a Desktop orphan row in " + table + ": " + row);
            } else {
                throw new IllegalStateException(
                        table
                                + " has a row for an element not in network "
                                + network
                                + ", and it isn't the known orphan pattern: "
                                + row);
            }
        }
        return logical;
    }

    private static String elementsOf(String table) {
        return switch (table) {
            case "defaultnode" -> "nodes";
            case "defaultedge" -> "edges";
            default -> null;
        };
    }

    /** Only the SUID and {@code selected} at its default, false; every other column empty. */
    static boolean isOrphan(JsonObject row) {
        for (var entry : row.entrySet()) {
            String column = entry.getKey();
            JsonElement value = entry.getValue();
            if (column.equals("SUID")) {
                continue;
            }
            if (column.equals("selected")) {
                if (!value.isJsonNull() && value.getAsBoolean()) {
                    return false;
                }
                continue;
            }
            if (!value.isJsonNull()) {
                return false;
            }
        }
        return true;
    }

    private JsonObject row(long network, String table, long id) {
        for (JsonObject row : rows(network, table)) {
            if (row.has("cxid")
                    && !row.get("cxid").isJsonNull()
                    && row.get("cxid").getAsLong() == id) {
                return row;
            }
        }
        return null;
    }

    private Map<Long, Long> suids(String table) {
        Map<Long, Long> suids = new LinkedHashMap<>();
        for (JsonObject row : rows(target, table)) {
            if (row.has("cxid") && !row.get("cxid").isJsonNull()) {
                suids.put(row.get("cxid").getAsLong(), row.get("SUID").getAsLong());
            }
        }
        return suids;
    }

    private Map<String, String> columnTypes(long network, String table) {
        Map<String, String> types = new LinkedHashMap<>();
        rest("GET", "/v1/networks/" + network + "/tables/" + table + "/columns", null)
                .getAsJsonArray()
                .forEach(
                        column ->
                                types.put(
                                        column.getAsJsonObject().get("name").getAsString(),
                                        column.getAsJsonObject().get("type").getAsString()));
        return types;
    }

    private static Object value(JsonElement value, String type) {
        return switch (type) {
            case "Integer" -> value.getAsInt();
            case "Long" -> value.getAsLong();
            case "Double" -> value.getAsDouble();
            case "Boolean" -> value.getAsBoolean();
            case "String" -> value.getAsString();
            default -> value.toString();
        };
    }

    private CyRest.Reply command(String namespace, String command, Map<String, String> args) {
        try {
            CyRest.Reply reply = cyrest.post(namespace, command, args);
            if (!reply.ok()) {
                throw new IllegalStateException(namespace + " " + command + " failed: " + reply);
            }
            return reply;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private JsonElement rest(String method, String path, JsonElement body) {
        try {
            return cyrest.rest(method, path, body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A rejected command's message, without CyREST's wrapping. */
    static String errorMessage(CyRest.Reply reply) {
        JsonArray errors = reply.errors().getAsJsonArray();
        return errors.isEmpty()
                ? reply.toString()
                : errors.get(0).getAsJsonObject().get("message").getAsString();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
