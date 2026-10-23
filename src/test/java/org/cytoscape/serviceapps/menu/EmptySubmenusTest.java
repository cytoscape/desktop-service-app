package org.cytoscape.serviceapps.menu;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.cytoscape.application.swing.CyAction;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.placement.MenuPlacement;
import org.cytoscape.serviceapps.placement.MenuPlacementResolver;
import org.cytoscape.serviceapps.testsupport.DesktopMenus;

import static org.cytoscape.serviceapps.testsupport.DesktopMenus.onEdtAndWait;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The submenu cleanup against Desktop's real menu bar: what Desktop leaves behind, what the purge
 * removes, and every case where it must change nothing.
 */
class EmptySubmenusTest {

    private final DesktopMenus desktop = new DesktopMenus();
    private final CySwingApplication swing = desktop.swing();
    private final EmptySubmenus submenus = new EmptySubmenus(swing);
    private final List<CyAction> added = new ArrayList<>();

    /** updatetablesexample's menu: Tools > Example Service App[0] > Update tables example[1]. */
    private final MenuPlacement example =
            placement("Tools", "Example Service App", 0, "Update tables example", 1);

    /** menu-multi-level's: Apps > Fixture[10] > Clustering[20] > Hello[30]. */
    private final MenuPlacement multiLevel =
            placement("Apps", "Fixture", 10, "Clustering", 20, "Hello", 30);

    @AfterEach
    void noPlaceholderIsEverLeft() {
        for (String top : DesktopMenus.TOP_MENUS) {
            for (String line : desktop.tree(top)) {
                assertFalse(line.contains(EmptySubmenus.PLACEHOLDER), line);
            }
        }
    }

    /** A placement as the resolver makes it from a service app's cyWebMenuItem. */
    private static MenuPlacement placement(String root, Object... nameGravityPairs) {
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < nameGravityPairs.length; i += 2) {
            path.append(i == 0 ? "" : ",")
                    .append("{\"name\":\"")
                    .append(nameGravityPairs[i])
                    .append("\",\"gravity\":")
                    .append(nameGravityPairs[i + 1])
                    .append('}');
        }
        ServiceMetadata meta =
                Json.parse(
                        "{\"name\":\"App\",\"version\":\"1\",\"cyWebMenuItem\":{\"root\":\""
                                + root
                                + "\",\"path\":["
                                + path
                                + "]}}",
                        ServiceMetadata.class);
        return MenuPlacementResolver.resolve(meta).orElseThrow();
    }

    /** The app's menu entry, as Desktop adds it for the registered task factory. */
    private CyAction entry(MenuPlacement placement) {
        return add(
                DesktopMenus.item(
                        placement.preferredMenu(), placement.title(), (float) placement.gravity()));
    }

    private CyAction add(CyAction action) {
        onEdtAndWait(() -> swing.addAction(action));
        added.add(action);
        return action;
    }

    private void remove(CyAction action) {
        onEdtAndWait(() -> swing.removeAction(action));
    }

    private List<String> purge(MenuPlacement placement) {
        List<List<String>> kept = new ArrayList<>();
        onEdtAndWait(() -> kept.add(submenus.purgeNow("App", placement)));
        return kept.get(0);
    }

    @Test
    void desktopAloneLeavesTheEmptiedSubmenuBehind() {
        assertEquals("Tools.Example Service App[0.0]", example.preferredMenu());
        CyAction entry = entry(example);
        assertEquals(
                List.of("Tools > Example Service App > Update tables example"),
                desktop.tree("Tools"));

        remove(entry);

        assertEquals(List.of("Tools > Example Service App (empty)"), desktop.tree("Tools"));
    }

    @Test
    void theEmptiedSubmenuIsRemoved() {
        remove(entry(example));

        List<String> kept = purge(example);

        assertEquals(List.of(), kept);
        assertEquals(List.of(), desktop.tree("Tools"));
    }

    @Test
    void everyEmptiedLevelOfADeeperPathIsRemoved() {
        remove(entry(multiLevel));
        assertEquals(List.of("Apps > Fixture > Clustering (empty)"), desktop.tree("Apps"));

        assertEquals(List.of(), purge(multiLevel));

        assertEquals(List.of(), desktop.tree("Apps"));
    }

    @Test
    void aSubmenuAnotherItemStillUsesIsKept() {
        add(DesktopMenus.item("Tools.Example Service App", "Another app's item", 5));
        remove(entry(example));

        List<String> kept = purge(example);

        assertEquals(List.of("Tools > Example Service App"), kept);
        assertEquals(
                List.of("Tools > Example Service App > Another app's item"), desktop.tree("Tools"));
    }

    @Test
    void onlyTheEmptiedInnerLevelGoesWhenAnOuterOneIsStillInUse() {
        add(DesktopMenus.item("Apps.Fixture", "Another fixture item", 5));
        remove(entry(multiLevel));

        List<String> kept = purge(multiLevel);

        assertEquals(List.of("Apps > Fixture"), kept);
        assertEquals(List.of("Apps > Fixture > Another fixture item"), desktop.tree("Apps"));
    }

    @Test
    void anEntryStillInItsSubmenuIsNeverRemoved() {
        // As if the purge ran before Desktop removed the entry.
        entry(example);
        List<String> before = desktop.tree("Tools");

        List<String> kept = purge(example);

        assertEquals(List.of("Tools > Example Service App"), kept);
        assertEquals(before, desktop.tree("Tools"));
    }

    @Test
    void purgingAgainOrWithNothingThereChangesNothing() {
        add(DesktopMenus.item("Tools", "Tools item", 50));
        remove(entry(example));
        purge(example);
        List<String> after = desktop.tree("Tools");

        assertEquals(List.of(), purge(example));
        assertEquals(List.of(), purge(multiLevel)); // a path that never existed
        assertEquals(after, desktop.tree("Tools"));
        assertEquals(List.of("Tools > Tools item"), after);
        assertEquals(List.of(), desktop.tree("Apps"));
    }

    @Test
    void aReinstalledEntryAppearsAgainWithItsSubmenuGravity() {
        add(DesktopMenus.item("Tools", "Gravity 5 item", 5));
        CyAction first = entry(example);
        List<String> installed = desktop.tree("Tools");
        assertEquals(
                List.of(
                        "Tools > Example Service App > Update tables example",
                        "Tools > Gravity 5 item"),
                installed,
                "gravity 0 puts the submenu first");
        remove(first);
        purge(example);

        entry(example);

        assertEquals(installed, desktop.tree("Tools"), "attached, visible and first again");
    }

    @Test
    void anEntryWithoutSubmenusOrDesktopMenusIsLeftAlone() {
        MenuPlacement noSubmenu = placement("Apps", "Hello", 20);
        assertTrue(noSubmenu.submenus().isEmpty());
        CySwingApplication untouched = mock(CySwingApplication.class);

        new EmptySubmenus(untouched).purge("App", noSubmenu);
        EmptySubmenus.NONE.purge("App", example);

        verifyNoInteractions(untouched);
    }
}
