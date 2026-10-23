package org.cytoscape.serviceapps.fixture;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.model.Parameter;
import org.cytoscape.serviceapps.model.ResultAction;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.model.TaskStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The catalogue is the input for most tests, so its coverage is checked here. */
class ScenarioCatalogTest {

    @Test
    void everyScenarioServesValidMetadata() {
        for (Scenario scenario : ScenarioCatalog.all().values()) {
            ServiceMetadata meta =
                    Json.parse(scenario.metadata().toString(), ServiceMetadata.class);
            assertNotNull(meta.name, scenario.name());
            assertNotNull(meta.version, scenario.name());
            assertNotNull(meta.serviceInputDefinition, scenario.name());
            TaskStatus status = new TaskStatus();
            status.result = scenario.result();
            assertFalse(status.actions().isEmpty(), scenario.name());
        }
    }

    @Test
    void coversEveryParameterType() {
        Set<String> types =
                ScenarioCatalog.all().values().stream()
                        .flatMap(
                                s ->
                                        Json.parse(s.metadata().toString(), ServiceMetadata.class)
                                                .parameters()
                                                .stream())
                        .map(p -> p.type)
                        .collect(Collectors.toSet());

        for (String type :
                List.of(
                        Parameter.TEXT,
                        Parameter.DROP_DOWN,
                        Parameter.RADIO,
                        Parameter.CHECK_BOX,
                        Parameter.NODE_COLUMN,
                        Parameter.EDGE_COLUMN,
                        Parameter.FILE,
                        Parameter.ACCESS_TOKEN,
                        Parameter.NDEX_UUID)) {
            assertTrue(types.contains(type), type);
        }
    }

    @Test
    void coversEveryTextValidationTypeAndColumnFilter() {
        Set<String> validation =
                parametersOf("param-text-string", "param-text-number", "param-text-digits").stream()
                        .map(p -> p.validationType)
                        .collect(Collectors.toSet());
        assertEquals(Set.of("string", "number", "digits"), validation);

        for (String scenario : List.of("param-node-column-filters", "param-edge-column-filters")) {
            Set<String> filters =
                    parametersOf(scenario).stream()
                            .map(p -> p.columnTypeFilter == null ? "" : p.columnTypeFilter)
                            .collect(Collectors.toSet());
            assertEquals(Set.copyOf(ScenarioCatalog.COLUMN_TYPE_FILTERS), filters, scenario);
        }
    }

    @Test
    void coversEveryResultActionFromTheSpec() {
        assertEquals(Set.copyOf(ResultAction.ALL), ScenarioCatalog.RESULT_SCENARIOS.keySet());
        ScenarioCatalog.RESULT_SCENARIOS.forEach(
                (action, scenario) -> {
                    TaskStatus status = new TaskStatus();
                    status.result = ScenarioCatalog.get(scenario).result();
                    assertEquals(action, status.actions().get(0).action, scenario);
                });
    }

    @Test
    void coversTheMenuPlacementCases() {
        assertTrue(ScenarioCatalog.MENU_ROOTS.containsKey("menu-root-missing"));
        Set<String> roots =
                ScenarioCatalog.MENU_ROOTS.values().stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        for (String root :
                List.of(
                        "",
                        "Apps",
                        "Layout",
                        "Tools",
                        "tools",
                        "Edit",
                        "Help",
                        "Analysis",
                        "Data",
                        "Unknown123")) {
            assertTrue(roots.contains(root), root);
        }
        ScenarioCatalog.MENU_ROOTS
                .keySet()
                .forEach(name -> assertNotNull(ScenarioCatalog.get(name), name));
    }

    @Test
    void helloAndItsUpgradeShareAName() {
        Scenario hello = ScenarioCatalog.get(ScenarioCatalog.HELLO);
        Scenario upgrade = ScenarioCatalog.get(ScenarioCatalog.HELLO_V2);

        assertEquals("Hello World", hello.appName());
        assertEquals(hello.appName(), upgrade.appName());
        assertEquals("1.0", hello.appVersion());
        assertEquals("2.0", upgrade.appVersion());
    }

    private static List<Parameter> parametersOf(String... scenarios) {
        return Arrays.stream(scenarios)
                .flatMap(
                        name ->
                                Json.parse(
                                        ScenarioCatalog.get(name).metadata().toString(),
                                        ServiceMetadata.class)
                                        .parameters()
                                        .stream())
                .toList();
    }
}
