package org.cytoscape.serviceapps.placement;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.cytoscape.serviceapps.fixture.ScenarioCatalog;
import org.cytoscape.serviceapps.model.CyWebMenuItem;
import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.model.ServiceMetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The menu placement table in cytoscape/cytoscape#44. */
class MenuPlacementResolverTest {

    private static ServiceMetadata meta(String root, Object... nameGravityPairs) {
        ServiceMetadata meta = new ServiceMetadata();
        meta.name = "Hello World";
        meta.cyWebMenuItem = new CyWebMenuItem();
        meta.cyWebMenuItem.root = root;
        meta.cyWebMenuItem.path = new java.util.ArrayList<>();
        for (int i = 0; i < nameGravityPairs.length; i += 2) {
            CyWebMenuItem.PathElement element = new CyWebMenuItem.PathElement();
            element.name = (String) nameGravityPairs[i];
            element.gravity = (Double) nameGravityPairs[i + 1];
            meta.cyWebMenuItem.path.add(element);
        }
        return meta;
    }

    static Stream<Arguments> placed() {
        return Stream.of(
                Arguments.of(
                        "Apps",
                        List.of("Example", 10.0, "Hello", 20.0),
                        "Apps",
                        List.of("Example"),
                        "Apps.Example[10.0]",
                        "Hello",
                        20.0),
                Arguments.of(
                        "Layout",
                        List.of("Hello", 5.0),
                        "Layout",
                        List.of(),
                        "Layout",
                        "Hello",
                        5.0),
                Arguments.of(
                        "Tools",
                        List.of("Example", 1.0, "Hello", 2.0),
                        "Tools",
                        List.of("Example"),
                        "Tools.Example[1.0]",
                        "Hello",
                        2.0),
                Arguments.of(
                        "tools", List.of("Hello", 2.0), "Tools", List.of(), "Tools", "Hello", 2.0),
                Arguments.of(
                        " Tools ",
                        List.of("Hello", 2.0),
                        "Tools",
                        List.of(),
                        "Tools",
                        "Hello",
                        2.0),
                Arguments.of(
                        "Edit", List.of("Hello", 2.0), "Edit", List.of(), "Edit", "Hello", 2.0),
                Arguments.of(
                        "Help", List.of("Hello", 2.0), "Help", List.of(), "Help", "Hello", 2.0),
                Arguments.of(
                        "File", List.of("Hello", 2.0), "File", List.of(), "File", "Hello", 2.0),
                Arguments.of(
                        "View", List.of("Hello", 2.0), "View", List.of(), "View", "Hello", 2.0),
                Arguments.of(
                        "Select",
                        List.of("Hello", 2.0),
                        "Select",
                        List.of(),
                        "Select",
                        "Hello",
                        2.0),
                Arguments.of(
                        "Apps",
                        List.of(),
                        "Apps",
                        List.of(),
                        "Apps",
                        "Hello World",
                        MenuPlacementResolver.DEFAULT_GRAVITY));
    }

    @ParameterizedTest(name = "root [{0}] path {1}")
    @MethodSource("placed")
    void placesAnEntryUnderADesktopMenu(
            String root,
            List<Object> path,
            String topMenu,
            List<String> submenus,
            String preferredMenu,
            String title,
            double gravity) {
        MenuPlacement placement =
                MenuPlacementResolver.resolve(meta(root, path.toArray())).orElseThrow();

        assertEquals(topMenu, placement.topMenu());
        assertEquals(submenus, placement.submenus());
        assertEquals(preferredMenu, placement.preferredMenu());
        assertEquals(title, placement.title());
        assertEquals(gravity, placement.gravity());
        assertTrue(MenuPlacementResolver.whyNoPlacement(meta(root, path.toArray())).isEmpty());
    }

    static Stream<Arguments> notPlaced() {
        return Stream.of(
                Arguments.of(null, "its metadata names no menu"),
                Arguments.of("", "its metadata names no menu"),
                Arguments.of("  ", "its metadata names no menu"),
                Arguments.of("Analysis", "'Analysis' is not a Cytoscape Desktop menu"),
                Arguments.of("Data", "'Data' is not a Cytoscape Desktop menu"),
                Arguments.of("Unknown123", "'Unknown123' is not a Cytoscape Desktop menu"));
    }

    @ParameterizedTest(name = "root [{0}]")
    @MethodSource("notPlaced")
    void createsNoEntryOtherwise(String root, String reason) {
        ServiceMetadata meta = meta(root, "Hello", 20.0);

        assertEquals(Optional.empty(), MenuPlacementResolver.resolve(meta));
        assertEquals(Optional.of(reason), MenuPlacementResolver.whyNoPlacement(meta));
    }

    @Test
    void anAppWithNoMenuItemAtAllGetsNoEntry() {
        ServiceMetadata meta = new ServiceMetadata();
        meta.name = "x";

        assertTrue(MenuPlacementResolver.resolve(meta).isEmpty());
        assertTrue(MenuPlacementResolver.resolve(null).isEmpty());
    }

    @Test
    void namesCannotBreakDesktopsMenuPath() {
        MenuPlacement placement =
                MenuPlacementResolver.resolve(meta("Apps", "v1.2 tools", 1.0, "Run [beta]", 2.0))
                        .orElseThrow();

        assertEquals("Apps.v1 2 tools[1.0]", placement.preferredMenu());
        assertEquals("Run (beta)", placement.title());
    }

    @Test
    void submenusWithoutGravityAreLeftToDesktop() {
        MenuPlacement placement =
                MenuPlacementResolver.resolve(meta("Apps", "Example", null, "Hello", null))
                        .orElseThrow();

        assertEquals("Apps.Example", placement.preferredMenu());
        assertEquals(MenuPlacementResolver.DEFAULT_GRAVITY, placement.gravity());
        assertEquals("Apps > Example > Hello", placement.describe());
    }

    @Test
    void agreesWithEveryFixtureMenuScenario() {
        ScenarioCatalog.MENU_ROOTS.forEach(
                (scenario, root) -> {
                    ServiceMetadata meta =
                            Json.parse(
                                    ScenarioCatalog.get(scenario).metadata().toString(),
                                    ServiceMetadata.class);
                    boolean desktopMenu =
                            root != null
                                    && MenuPlacementResolver.DESKTOP_MENUS.stream()
                                            .anyMatch(m -> m.equalsIgnoreCase(root.strip()));
                    assertEquals(
                            desktopMenu, MenuPlacementResolver.resolve(meta).isPresent(), scenario);
                });
    }
}
