package org.cytoscape.serviceapps.fixture;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The named fixture scenarios that tests select by name.
 *
 * <p>The catalogue covers every parameter type and attribute variant, input columns, menu placement
 * cases, unsupported inputs, every result action from the spec, and failure behaviours. Bump {@link
 * #CATALOG_VERSION} when a scenario's meaning changes, so tests that depend on it are revisited.
 */
public final class ScenarioCatalog {

    public static final int CATALOG_VERSION = 1;

    /** The baseline app: name "Hello World", version 1.0, one text parameter. */
    public static final String HELLO = "hello";

    /** The same app name as {@link #HELLO} at version 2.0, for upgrade tests. */
    public static final String HELLO_V2 = "hello-v2";

    /** The 13 menu placement cases, keyed by scenario name, valued by the {@code root} sent. */
    public static final Map<String, String> MENU_ROOTS;

    /** One result scenario per action, keyed by action name. */
    public static final Map<String, String> RESULT_SCENARIOS;

    /** The example CX2 network from the spec's addNetworks and updateNetwork pages. */
    public static final String EXAMPLE_CX2 =
            "[{\"CXVersion\":\"2.0\",\"hasFragments\":false},"
                    + "{\"metaData\":[{\"elementCount\":1,\"name\":\"attributeDeclarations\"},"
                    + "{\"elementCount\":1,\"name\":\"networkAttributes\"},"
                    + "{\"elementCount\":2,\"name\":\"nodes\"},"
                    + "{\"elementCount\":1,\"name\":\"edges\"}]},"
                    + "{\"attributeDeclarations\":[{\"networkAttributes\":{\"name\":{\"d\":\"string\"}},"
                    + "\"nodes\":{\"name\":{\"d\":\"string\"},\"age\":{\"d\":\"integer\"}},"
                    + "\"edges\":{\"weight\":{\"d\":\"double\"}}}]},"
                    + "{\"networkAttributes\":[{\"name\":\"network name\"}]},"
                    + "{\"nodes\":[{\"id\":0,\"v\":{\"name\":\"node 1\",\"age\":5}},"
                    + "{\"id\":1,\"v\":{\"name\":\"node 2\",\"age\":10}}]},"
                    + "{\"edges\":[{\"id\":0,\"s\":0,\"t\":1,\"v\":{\"weight\":0.3}}]},"
                    + "{\"status\":[{\"error\":\"\",\"success\":true}]}]";

    /** Every column type filter the spec names, plus none. */
    public static final List<String> COLUMN_TYPE_FILTERS =
            List.of(
                    "",
                    "string",
                    "long",
                    "integer",
                    "double",
                    "boolean",
                    "list_of_string",
                    "list_of_long",
                    "list_of_integer",
                    "list_of_double",
                    "list_of_boolean",
                    "number",
                    "wholenumber",
                    "list",
                    "list_of_number",
                    "list_of_wholenumber");

    private static final Map<String, Scenario> ALL;

    static {
        Map<String, Scenario> all = new LinkedHashMap<>();

        // Baseline and upgrade
        add(all, HELLO, "Hello World", "1.0", base(), defaultResult());
        add(all, HELLO_V2, "Hello World", "2.0", base(), defaultResult());

        // Parameter types and attribute variants
        add(
                all,
                "param-text-string",
                withParams(
                        param("Algorithm", "text")
                                .add("validationType", "string")
                                .add("validationRegex", "louvain|leiden")
                                .add("validationHelp", "Must be louvain or leiden")
                                .add("defaultValue", "louvain")
                                .build()));
        add(
                all,
                "param-text-number",
                withParams(
                        param("Resolution", "text")
                                .add("validationType", "number")
                                .add("minValue", 0.0)
                                .add("maxValue", 1.0)
                                .add("validationHelp", "Must be a number from 0 to 1")
                                .add("defaultValue", "0.5")
                                .build()));
        add(
                all,
                "param-text-digits",
                withParams(
                        param("Iterations", "text")
                                .add("validationType", "digits")
                                .add("minValue", -10.0)
                                .add("maxValue", 10.0)
                                .add("validationHelp", "Must be a whole number from -10 to 10")
                                .add("defaultValue", "3")
                                .build()));
        add(all, "param-text-no-default", withParams(param("Label", "text").build()));
        add(
                all,
                "param-dropdown",
                withParams(
                        param("Method", "dropDown")
                                .add("valueList", list("louvain", "leiden", "infomap"))
                                .add("defaultValue", "leiden")
                                .build()));
        add(
                all,
                "param-dropdown-no-default",
                withParams(
                        param("Method", "dropDown")
                                .add("valueList", list("louvain", "leiden"))
                                .build()));
        add(
                all,
                "param-radio",
                withParams(
                        param("Direction", "radio")
                                .add("valueList", list("up", "down"))
                                .add("defaultValue", "down")
                                .build()));
        add(
                all,
                "param-radio-no-default",
                withParams(
                        param("Direction", "radio").add("valueList", list("up", "down")).build()));
        add(
                all,
                "param-checkbox-true",
                withParams(param("Weighted", "checkBox").add("defaultValue", true).build()));
        add(
                all,
                "param-checkbox-false",
                withParams(param("Weighted", "checkBox").add("defaultValue", false).build()));
        add(all, "param-node-column-filters", columnFilterParams("nodeColumn"));
        add(all, "param-edge-column-filters", columnFilterParams("edgeColumn"));
        // Two parameters with the same name in the same groups: only the last is used
        add(
                all,
                "param-shared-key",
                withParams(
                        param("Gap", "text").add("groups", list("Nodes")).build(),
                        param("Gap", "text").add("groups", list("Nodes")).build()));
        add(
                all,
                "param-column-filter-list",
                withParams(
                        param("Score", "nodeColumn")
                                .add("columnTypeFilter", list("wholenumber", "list_of_string"))
                                .build()));
        // Cytoscape Web's groups: nesting in first-seen order, and its key rule for shared names
        add(
                all,
                "param-groups",
                withParams(
                        param("Last name", "text")
                                .add("groups", list("Company", "Department", "Office"))
                                .build(),
                        param("First name", "text")
                                .add("groups", list("Company", "Department", "Office", "Identity"))
                                .build(),
                        param("Top level", "text").build(),
                        param("Office name", "text")
                                .add("groups", list("Company", "Department", "Office"))
                                .build(),
                        param("Gap", "text")
                                .add("groups", list("Nodes"))
                                .add("defaultValue", "n")
                                .build(),
                        param("Gap", "text")
                                .add("groups", list("Clusters"))
                                .add("defaultValue", "c")
                                .build()));

        // Input columns and input definitions
        add(all, "input-columns", inputColumns("node"));
        add(all, "input-edge-columns", inputColumns("edge"));
        add(all, "input-network-graph", inputNetwork("graph", "cx2"));
        add(all, "input-none", inputNone());
        JsonObject typeNone = inputNone();
        typeNone.getAsJsonObject("serviceInputDefinition").addProperty("type", "none");
        add(all, "input-type-none", typeNone);

        // Dialog description
        JsonObject shown = base();
        shown.addProperty("showDescriptionInDialog", true);
        add(all, "description-shown", shown);
        JsonObject hidden = base();
        hidden.addProperty("showDescriptionInDialog", false);
        add(all, "description-hidden", hidden);

        // An ndexUUID parameter, which Desktop fills with the network's NDEx URL, beside one the
        // user fills
        add(
                all,
                "param-ndex-uuid",
                withParams(
                        param("NDEx network", "ndexUUID").build(),
                        param("K", "text")
                                .add("validationType", "number")
                                .add("defaultValue", "10")
                                .build()));
        // The same parameter on an app that takes no data, which runs with no network open
        JsonObject ndexNoInput = inputNone();
        ndexNoInput.getAsJsonObject("serviceInputDefinition").addProperty("type", "none");
        JsonArray ndexParameter = new JsonArray();
        ndexParameter.add(param("NDEx network", "ndexUUID").build());
        ndexNoInput.add("parameters", ndexParameter);
        JsonArray openUrl = new JsonArray();
        openUrl.add(action("openURL", json("{\"url\":\"https://www.cytoscape.org\"}")));
        add(all, "param-ndex-uuid-no-input", declaring(ndexNoInput, "openURL"), openUrl);

        // Unsupported inputs
        add(all, "unsupported-file", withParams(param("Input file", "file").build()));
        add(all, "unsupported-access-token", withParams(param("Token", "accessToken").build()));
        add(all, "unsupported-edgelist", inputNetwork("network", "edgelist"));

        // Menu placement
        Map<String, String> roots = new LinkedHashMap<>();
        roots.put("menu-root-missing", null);
        roots.put("menu-root-blank", "");
        roots.put("menu-root-apps", "Apps");
        roots.put("menu-root-layout", "Layout");
        roots.put("menu-root-layouts", "Layouts");
        roots.put("menu-root-tools", "Tools");
        roots.put("menu-root-tools-lowercase", "tools");
        roots.put("menu-root-edit", "Edit");
        roots.put("menu-root-help", "Help");
        roots.put("menu-root-analysis", "Analysis");
        roots.put("menu-root-data", "Data");
        roots.put("menu-root-unknown", "Unknown123");
        for (Map.Entry<String, String> root : roots.entrySet()) {
            add(all, root.getKey(), withMenu(root.getValue(), menuPath("Hello", 20)));
        }
        add(
                all,
                "menu-multi-level",
                withMenu("Apps", menuPath("Fixture", 10, "Clustering", 20, "Hello", 30)));
        add(all, "menu-empty-path", withMenu("Apps", new JsonArray()));
        roots.put("menu-multi-level", "Apps");
        roots.put("menu-empty-path", "Apps");
        MENU_ROOTS = Collections.unmodifiableMap(roots);

        // One result scenario per action
        Map<String, String> results = new LinkedHashMap<>();
        results.put("addNetworks", "result-add-networks");
        results.put("updateNetwork", "result-update-network");
        results.put("addTables", "result-add-tables");
        results.put("updateTables", "result-update-tables");
        results.put("updateLayouts", "result-update-layouts");
        results.put("updateSelection", "result-update-selection");
        results.put("openURL", "result-open-url");
        RESULT_SCENARIOS = Collections.unmodifiableMap(results);
        for (Map.Entry<String, String> entry : results.entrySet()) {
            JsonArray result = new JsonArray();
            result.add(exampleAction(entry.getKey()));
            add(all, entry.getValue(), declaring(base(), entry.getKey()), result);
        }
        JsonArray noTarget = new JsonArray();
        noTarget.add(action("openURL", json("{\"url\":\"https://www.cytoscape.org\"}")));
        add(all, "result-open-url-no-target", declaring(base(), "openURL"), noTarget);

        JsonArray multi = new JsonArray();
        multi.add(exampleAction("updateTables"));
        multi.add(exampleAction("updateLayouts"));
        multi.add(exampleAction("updateSelection"));
        add(
                all,
                "result-multi-action",
                declaring(base(), "updateTables", "updateLayouts", "updateSelection"),
                multi);

        JsonArray unknown = new JsonArray();
        unknown.add(action("explodeNetwork", json("{}")));
        unknown.add(exampleAction("updateSelection"));
        add(all, "result-unknown-action", declaring(base(), "updateSelection"), unknown);

        JsonArray undeclared = new JsonArray();
        undeclared.add(exampleAction("updateSelection"));
        add(all, "result-undeclared-action", declaring(base(), "updateLayouts"), undeclared);

        // Results that fail while being applied (fail fast)
        JsonArray failFast = new JsonArray();
        failFast.add(exampleAction("updateLayouts"));
        failFast.add(
                action("updateTables", json("{\"id\":\"network\",\"columns\":[],\"rows\":{}}")));
        failFast.add(exampleAction("updateSelection"));
        add(
                all,
                "result-fail-fast",
                declaring(base(), "updateLayouts", "updateTables", "updateSelection"),
                failFast);

        JsonArray invalidCx2 = new JsonArray();
        invalidCx2.add(
                action(
                        "updateNetwork",
                        json(
                                "[{\"CXVersion\":\"2.0\"},{\"nodes\":[{\"id\":0}]},"
                                        + "{\"edges\":[{\"id\":9,\"s\":0,\"t\":42}]}]")));
        add(all, "result-invalid-cx2", declaring(base(), "updateNetwork"), invalidCx2);

        JsonArray malformedLayouts = new JsonArray();
        malformedLayouts.add(action("updateLayouts", json("{\"id\":7,\"x\":1,\"y\":2}")));
        add(all, "result-malformed-layouts", declaring(base(), "updateLayouts"), malformedLayouts);

        JsonArray unknownColumnType = new JsonArray();
        unknownColumnType.add(
                action(
                        "updateTables",
                        json(
                                "{\"id\":\"node\",\"columns\":[{\"id\":\"shape\",\"type\":\"complex\"}],"
                                        + "\"rows\":{\"1\":{\"shape\":\"round\"}}}")));
        add(
                all,
                "result-unknown-column-type",
                declaring(base(), "updateTables"),
                unknownColumnType);

        JsonArray iframe = new JsonArray();
        iframe.add(
                action(
                        "openURL",
                        json("{\"url\":\"https://www.cytoscape.org\",\"target\":\"frame\"}")));
        add(all, "result-open-url-iframe", declaring(base(), "openURL"), iframe);

        // Failure behaviours
        all.put(
                "failure-slow",
                scenario(
                        "failure-slow",
                        base(),
                        defaultResult(),
                        Scenario.Behavior.NORMAL
                                .withProgressSteps(
                                        List.of(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100))
                                .withLatency(50)));
        all.put(
                "failure-failed",
                scenario(
                        "failure-failed",
                        base(),
                        defaultResult(),
                        Scenario.Behavior.NORMAL.withFinal(
                                "failed", "Algorithm failed: bad input")));
        all.put(
                "failure-never-complete",
                scenario(
                        "failure-never-complete",
                        base(),
                        defaultResult(),
                        Scenario.Behavior.NORMAL
                                .withProgressSteps(List.of(0, 50))
                                .neverFinishing()));
        all.put(
                "failure-submit-500",
                scenario(
                        "failure-submit-500",
                        base(),
                        defaultResult(),
                        Scenario.Behavior.NORMAL.withSubmitStatus(500)));

        ALL = Collections.unmodifiableMap(all);
    }

    private ScenarioCatalog() {}

    public static Map<String, Scenario> all() {
        return ALL;
    }

    /**
     * @throws IllegalArgumentException if there is no scenario with this name
     */
    public static Scenario get(String name) {
        Scenario scenario = ALL.get(name);
        if (scenario == null) {
            throw new IllegalArgumentException("no fixture scenario named '" + name + "'");
        }
        return scenario;
    }

    /** The example {@code data} for an action, from the spec's example page for that action. */
    public static JsonObject exampleAction(String action) {
        switch (action) {
            case "addNetworks":
                JsonArray networks = new JsonArray();
                networks.add(json(EXAMPLE_CX2));
                return action(action, networks);
            case "updateNetwork":
                return action(action, json(EXAMPLE_CX2));
            case "addTables":
                return action(
                        action,
                        json(
                                "{\"id\":\"enrichment\",\"columns\":[{\"id\":\"term\",\"type\":\"string\"}],"
                                        + "\"rows\":{\"1\":{\"term\":\"GO:0008150\"}}}"));
            case "updateTables":
                return action(
                        action,
                        json(
                                "{\"id\":\"node\",\"columns\":[{\"id\":\"count\",\"type\":\"integer\"},"
                                        + "{\"id\":\"notes\",\"type\":\"string\"}],"
                                        + "\"rows\":{\"1\":{\"count\":10,\"notes\":\"lowest\"},"
                                        + "\"2\":{\"count\":24},\"999\":{\"count\":1}}}"));
            case "updateLayouts":
                return action(
                        action,
                        json(
                                "[{\"id\":7,\"x\":25.2,\"y\":-473.6},{\"id\":100,\"x\":15,\"y\":100,\"z\":2}]"));
            case "updateSelection":
                return action(action, json("{\"nodes\":[0,15,25],\"edges\":[3,6]}"));
            case "openURL":
                return action(
                        action, json("{\"url\":\"https://www.cytoscape.org\",\"target\":null}"));
            default:
                throw new IllegalArgumentException("no example for action '" + action + "'");
        }
    }

    // --- builders ---

    private static void add(Map<String, Scenario> all, String name, JsonObject metadata) {
        add(all, name, metadata, defaultResult());
    }

    private static void add(
            Map<String, Scenario> all, String name, JsonObject metadata, JsonArray result) {
        all.put(name, scenario(name, metadata, result, Scenario.Behavior.NORMAL));
    }

    private static void add(
            Map<String, Scenario> all,
            String name,
            String appName,
            String version,
            JsonObject metadata,
            JsonArray result) {
        metadata.addProperty("name", appName);
        metadata.addProperty("version", version);
        all.put(name, new Scenario(name, metadata, result, Scenario.Behavior.NORMAL));
    }

    private static Scenario scenario(
            String name, JsonObject metadata, JsonArray result, Scenario.Behavior behavior) {
        if (!metadata.has("name")) {
            metadata.addProperty("name", "Fixture " + name);
        }
        return new Scenario(name, metadata, result, behavior);
    }

    private static JsonObject base() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("version", "1.0");
        metadata.addProperty("description", "A fixture service app for tests");
        metadata.addProperty("showDescriptionInDialog", false);
        metadata.addProperty("author", "Cytoscape team");
        metadata.addProperty("citation", "NA");
        metadata.add("cyWebActions", list("updateLayouts"));
        JsonObject menu = new JsonObject();
        menu.addProperty("root", "Apps");
        menu.add("path", menuPath("Fixture", 10, "Hello", 20));
        metadata.add("cyWebMenuItem", menu);
        JsonObject input = new JsonObject();
        input.addProperty("type", "network");
        input.addProperty("scope", "dynamic");
        JsonObject network = new JsonObject();
        network.addProperty("model", "network");
        network.addProperty("format", "cx2");
        input.add("inputNetwork", network);
        metadata.add("serviceInputDefinition", input);
        JsonArray parameters = new JsonArray();
        parameters.add(
                param("Greeting", "text")
                        .add("validationType", "string")
                        .add("defaultValue", "hello")
                        .build());
        metadata.add("parameters", parameters);
        return metadata;
    }

    private static JsonArray defaultResult() {
        JsonArray result = new JsonArray();
        result.add(exampleAction("updateLayouts"));
        return result;
    }

    private static JsonObject withParams(JsonObject... parameters) {
        JsonObject metadata = base();
        JsonArray list = new JsonArray();
        for (JsonObject parameter : parameters) {
            list.add(parameter);
        }
        metadata.add("parameters", list);
        return metadata;
    }

    private static JsonObject columnFilterParams(String type) {
        JsonObject metadata = base();
        JsonArray list = new JsonArray();
        for (String filter : COLUMN_TYPE_FILTERS) {
            ParamBuilder parameter =
                    param(type + " " + (filter.isEmpty() ? "any" : filter), type)
                            .add("columnTypeFilter", filter.isEmpty() ? null : filter);
            if ("string".equals(filter)) {
                parameter.add("defaultValue", "name");
            }
            list.add(parameter.build());
        }
        metadata.add("parameters", list);
        return metadata;
    }

    private static JsonObject inputColumns(String type) {
        JsonObject metadata = base();
        JsonObject input = new JsonObject();
        input.addProperty("type", type);
        input.addProperty("scope", "all");
        input.add(
                "inputColumns",
                json(
                        "[{\"name\":\"Gene\",\"description\":\"Gene symbol\",\"dataType\":\"string\","
                                + "\"allowMultipleSelection\":false,\"defaultColumnName\":\"name\"},"
                                + "{\"name\":\"Scores\",\"description\":\"Numeric scores\","
                                + "\"dataType\":\"number\",\"allowMultipleSelection\":true},"
                                + "{\"name\":\"Any\",\"description\":\"Any column\"},"
                                + "{\"columnName\":\"shared name\"}]"));
        metadata.add("serviceInputDefinition", input);
        return metadata;
    }

    private static JsonObject inputNetwork(String model, String format) {
        JsonObject metadata = base();
        JsonObject network = new JsonObject();
        network.addProperty("model", model);
        network.addProperty("format", format);
        metadata.getAsJsonObject("serviceInputDefinition").add("inputNetwork", network);
        return metadata;
    }

    private static JsonObject inputNone() {
        JsonObject metadata = base();
        JsonObject input = new JsonObject();
        input.add("type", JsonNull.INSTANCE);
        metadata.add("serviceInputDefinition", input);
        return metadata;
    }

    private static JsonObject withMenu(String root, JsonArray path) {
        JsonObject metadata = base();
        JsonObject menu = new JsonObject();
        if (root != null) {
            menu.addProperty("root", root);
        }
        menu.add("path", path);
        metadata.add("cyWebMenuItem", menu);
        return metadata;
    }

    private static JsonObject declaring(JsonObject metadata, String... actions) {
        metadata.add("cyWebActions", list(actions));
        return metadata;
    }

    private static JsonArray menuPath(Object... nameGravityPairs) {
        JsonArray path = new JsonArray();
        for (int i = 0; i < nameGravityPairs.length; i += 2) {
            JsonObject element = new JsonObject();
            element.addProperty("name", (String) nameGravityPairs[i]);
            element.addProperty("gravity", (Integer) nameGravityPairs[i + 1]);
            path.add(element);
        }
        return path;
    }

    private static JsonObject action(String action, JsonElement data) {
        JsonObject object = new JsonObject();
        object.addProperty("action", action);
        object.add("data", data);
        return object;
    }

    private static JsonArray list(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static ParamBuilder param(String displayName, String type) {
        return new ParamBuilder(displayName, type);
    }

    /** Builds one parameter with every spec attribute present, null unless set. */
    private static final class ParamBuilder {
        private final JsonObject parameter = new JsonObject();

        ParamBuilder(String displayName, String type) {
            parameter.addProperty("displayName", displayName);
            parameter.addProperty("description", "Help text for " + displayName);
            parameter.addProperty("type", type);
            for (String attribute :
                    List.of(
                            "valueList",
                            "defaultValue",
                            "validationType",
                            "columnTypeFilter",
                            "validationHelp",
                            "validationRegex",
                            "minValue",
                            "maxValue")) {
                parameter.add(attribute, JsonNull.INSTANCE);
            }
        }

        ParamBuilder add(String key, Object value) {
            if (value == null) {
                parameter.add(key, JsonNull.INSTANCE);
            } else if (value instanceof JsonElement element) {
                parameter.add(key, element);
            } else if (value instanceof String text) {
                parameter.addProperty(key, text);
            } else if (value instanceof Number number) {
                parameter.addProperty(key, number);
            } else if (value instanceof Boolean flag) {
                parameter.addProperty(key, flag);
            } else {
                throw new IllegalArgumentException("unsupported value " + value);
            }
            return this;
        }

        JsonObject build() {
            return parameter;
        }
    }
}
