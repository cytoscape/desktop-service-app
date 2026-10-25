package org.cytoscape.serviceapps.placement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.cytoscape.serviceapps.model.CyWebMenuItem;
import org.cytoscape.serviceapps.model.ServiceMetadata;

/**
 * Turns a service app's {@code cyWebMenuItem} into a Desktop menu location. The only code that
 * reads {@code root} or {@code path}.
 *
 * <p>{@code root} names one of Desktop's top-level menus, compared ignoring case and surrounding
 * spaces; {@code Layouts} means Layout, as in Cytoscape Web. An app with any other root, or none,
 * goes under Apps &gt; Service Apps with its path kept, as Cytoscape Web puts such apps under Apps.
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/cyWebMenuItem">cyWebMenuItem</a>
 */
public final class MenuPlacementResolver {

    /** Desktop's top-level menus. */
    public static final List<String> DESKTOP_MENUS =
            List.of("File", "Edit", "View", "Select", "Layout", "Apps", "Tools", "Help");

    /** Where an app whose root isn't a Desktop menu goes: Apps &gt; Service Apps. */
    public static final String FALLBACK_MENU = "Apps";

    public static final String FALLBACK_SUBMENU = "Service Apps";

    /** The gravity used when the service gives none. */
    public static final double DEFAULT_GRAVITY = 100.0;

    private static final String LAYOUTS = "layouts";

    private MenuPlacementResolver() {}

    /** The menu location, or empty when the app's entry would have no title. */
    public static Optional<MenuPlacement> resolve(ServiceMetadata meta) {
        if (meta == null) {
            return Optional.empty();
        }
        Optional<String> topMenu = topMenu(meta);
        String fallbackReason = topMenu.isPresent() ? null : fallbackReason(meta);
        List<CyWebMenuItem.PathElement> path = new ArrayList<>();
        if (meta.cyWebMenuItem != null) {
            for (CyWebMenuItem.PathElement element : meta.cyWebMenuItem.path()) {
                if (element != null && !clean(element.name).isEmpty()) {
                    path.add(element);
                }
            }
        }

        StringBuilder preferredMenu = new StringBuilder(topMenu.orElse(FALLBACK_MENU));
        List<String> submenus = new ArrayList<>();
        if (topMenu.isEmpty()) {
            submenus.add(FALLBACK_SUBMENU);
            preferredMenu.append('.').append(FALLBACK_SUBMENU);
        }
        for (int i = 0; i < path.size() - 1; i++) {
            String name = clean(path.get(i).name);
            submenus.add(name);
            preferredMenu.append('.').append(name);
            if (path.get(i).gravity != null) {
                preferredMenu.append('[').append(path.get(i).gravity).append(']');
            }
        }

        String title;
        double gravity;
        if (path.isEmpty()) {
            title = clean(meta.name);
            gravity = DEFAULT_GRAVITY;
        } else {
            CyWebMenuItem.PathElement last = path.get(path.size() - 1);
            title = clean(last.name);
            gravity = last.gravity == null ? DEFAULT_GRAVITY : last.gravity;
        }
        if (title.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                new MenuPlacement(
                        topMenu.orElse(FALLBACK_MENU),
                        List.copyOf(submenus),
                        preferredMenu.toString(),
                        title,
                        gravity,
                        fallbackReason));
    }

    private static String fallbackReason(ServiceMetadata meta) {
        String root = meta.cyWebMenuItem == null ? null : meta.cyWebMenuItem.root;
        if (root == null || root.isBlank()) {
            return "its metadata names no menu";
        }
        return "'" + root.strip() + "' is not a Cytoscape Desktop menu";
    }

    private static Optional<String> topMenu(ServiceMetadata meta) {
        if (meta.cyWebMenuItem == null || meta.cyWebMenuItem.root == null) {
            return Optional.empty();
        }
        String root = meta.cyWebMenuItem.root.strip().toLowerCase(Locale.ROOT);
        String menu = LAYOUTS.equals(root) ? "layout" : root;
        return DESKTOP_MENUS.stream()
                .filter(desktopMenu -> desktopMenu.toLowerCase(Locale.ROOT).equals(menu))
                .findFirst();
    }

    /**
     * Dots separate menu levels and brackets carry gravity in Desktop's menu paths, so they can't
     * appear inside a name.
     */
    static String clean(String name) {
        if (name == null) {
            return "";
        }
        return name.replace('.', ' ').replace('[', '(').replace(']', ')').strip();
    }
}
