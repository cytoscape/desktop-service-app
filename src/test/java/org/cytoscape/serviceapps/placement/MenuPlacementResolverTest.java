package org.cytoscape.serviceapps.placement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.cytoscape.serviceapps.fixture.ScenarioCatalog;
import org.cytoscape.serviceapps.manager.ManageServiceAppsAction;
import org.cytoscape.serviceapps.model.CyWebMenuItem;
import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.model.ServiceMetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Where each {@code cyWebMenuItem} puts an app's menu entry: the Desktop menu it names, or Apps
 * &gt; Service Apps.
 */
class MenuPlacementResolverTest {

    private static ServiceMetadata meta(String root, Object... nameGravityPairs) {
        ServiceMetadata meta = new ServiceMetadata();
        meta.name = "Hello World";
        meta.cyWebMenuItem = new CyWebMenuItem();
        meta.cyWebMenuItem.root = root;
        meta.cyWebMenuItem.path = new ArrayList<>();
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
                        "layouts",
                        List.of("Hello", 2.0),
                        "Layout",
                        List.of(),
                        "Layout",
                        "Hello",
                        2.0),
                Arguments.of(
                        " LAYOUTS ",
                        List.of("Example", 1.0, "Hello", 2.0),
                        "Layout",
                        List.of("Example"),
                        "Layout.Example[1.0]",
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
        assertNull(placement.fallbackReason());
    }

    static Stream<Arguments> fallback() {
        return Stream.of(
                Arguments.of(null, "its metadata names no menu"),
                Arguments.of("", "its metadata names no menu"),
                Arguments.of("  ", "its metadata names no menu"),
                Arguments.of("Analysis", "'Analysis' is not a Cytoscape Desktop menu"),
                Arguments.of("Data", "'Data' is not a Cytoscape Desktop menu"),
                Arguments.of("Unknown123", "'Unknown123' is not a Cytoscape Desktop menu"));
    }

    @ParameterizedTest(name = "root [{0}]")
    @MethodSource("fallback")
    void putsAnyOtherRootUnderServiceApps(String root, String reason) {
        MenuPlacement placement =
                MenuPlacementResolver.resolve(meta(root, "Hello", 20.0)).orElseThrow();

        assertEquals("Apps", placement.topMenu());
        assertEquals(List.of("Service Apps"), placement.submenus());
        assertEquals(ManageServiceAppsAction.MENU, placement.preferredMenu());
        assertEquals("Hello", placement.title());
        assertEquals(20.0, placement.gravity());
        assertEquals(reason, placement.fallbackReason());
        assertEquals("Apps > Service Apps > Hello", placement.describe());
    }

    @Test
    void aFallbackKeepsTheAppsOwnSubmenusAndGravities() {
        MenuPlacement placement =
                MenuPlacementResolver.resolve(
                                meta(
                                        "Analysis",
                                        "Community",
                                        5.0,
                                        "Clustering",
                                        null,
                                        "Detect",
                                        7.0))
                        .orElseThrow();

        assertEquals(List.of("Service Apps", "Community", "Clustering"), placement.submenus());
        assertEquals("Apps.Service Apps.Community[5.0].Clustering", placement.preferredMenu());
        assertEquals("Detect", placement.title());
        assertEquals(7.0, placement.gravity());
        assertEquals("Apps > Service Apps > Community > Clustering > Detect", placement.describe());
    }

    @Test
    void anAppWithNoMenuItemAtAllIsListedUnderServiceAppsByName() {
        ServiceMetadata meta = new ServiceMetadata();
        meta.name = "x";

        MenuPlacement placement = MenuPlacementResolver.resolve(meta).orElseThrow();
        assertEquals("Apps > Service Apps > x", placement.describe());
        assertEquals(MenuPlacementResolver.DEFAULT_GRAVITY, placement.gravity());
        assertEquals("its metadata names no menu", placement.fallbackReason());
    }

    @Test
    void anEntryWithoutATitleIsNotPlaced() {
        ServiceMetadata unnamed = new ServiceMetadata();
        unnamed.name = " ";

        assertEquals(Optional.empty(), MenuPlacementResolver.resolve(unnamed));
        assertEquals(Optional.empty(), MenuPlacementResolver.resolve(null));
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
    void namesAreNeverReadAsHtmlByDesktopsMenus() {
        MenuPlacement placement =
                MenuPlacementResolver.resolve(
                                meta(
                                        "Apps",
                                        "<html><img src='http://127.0.0.1:9/x.png'>",
                                        1.0,
                                        "<HTML>Run",
                                        2.0))
                        .orElseThrow();

        assertEquals(
                List.of("\u2039html><img src='http://127 0 0 1:9/x png'>"), placement.submenus());
        assertEquals("\u2039HTML>Run", placement.title());
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
                                    && (MenuPlacementResolver.DESKTOP_MENUS.stream()
                                                    .anyMatch(m -> m.equalsIgnoreCase(root.strip()))
                                            || "layouts".equalsIgnoreCase(root.strip()));
                    MenuPlacement placement = MenuPlacementResolver.resolve(meta).orElseThrow();
                    assertEquals(desktopMenu, placement.fallbackReason() == null, scenario);
                });
    }
}
