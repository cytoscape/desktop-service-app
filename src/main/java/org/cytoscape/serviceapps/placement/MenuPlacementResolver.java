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
 * <p>An app gets a menu entry only when {@code root} names one of Desktop's top-level menus,
 * compared ignoring case and surrounding spaces. In every other case there is no menu entry; the
 * app is still listed in the Service Apps manager and runnable from there.
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/cyWebMenuItem">cyWebMenuItem</a>
 */
public final class MenuPlacementResolver {

    /** Desktop's top-level menus. */
    public static final List<String> DESKTOP_MENUS =
            List.of("File", "Edit", "View", "Select", "Layout", "Apps", "Tools", "Help");

    /** The gravity used when the service gives none. */
    public static final double DEFAULT_GRAVITY = 100.0;

    private MenuPlacementResolver() {}

    /** The menu location, or empty when the app gets no menu entry. */
    public static Optional<MenuPlacement> resolve(ServiceMetadata meta) {
        Optional<String> topMenu = topMenu(meta);
        if (topMenu.isEmpty()) {
            return Optional.empty();
        }
        List<CyWebMenuItem.PathElement> path = new ArrayList<>();
        for (CyWebMenuItem.PathElement element : meta.cyWebMenuItem.path()) {
            if (element != null && !clean(element.name).isEmpty()) {
                path.add(element);
            }
        }

        StringBuilder preferredMenu = new StringBuilder(topMenu.get());
        List<String> submenus = new ArrayList<>();
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
                        topMenu.get(),
                        List.copyOf(submenus),
                        preferredMenu.toString(),
                        title,
                        gravity));
    }

    /** Why an app has no menu entry, for the log; empty when it has one. */
    public static Optional<String> whyNoPlacement(ServiceMetadata meta) {
        if (resolve(meta).isPresent()) {
            return Optional.empty();
        }
        String root = meta == null || meta.cyWebMenuItem == null ? null : meta.cyWebMenuItem.root;
        if (root == null || root.isBlank()) {
            return Optional.of("its metadata names no menu");
        }
        if (topMenu(meta).isEmpty()) {
            return Optional.of("'" + root.strip() + "' is not a Cytoscape Desktop menu");
        }
        return Optional.of("its menu entry has no title");
    }

    private static Optional<String> topMenu(ServiceMetadata meta) {
        if (meta == null || meta.cyWebMenuItem == null || meta.cyWebMenuItem.root == null) {
            return Optional.empty();
        }
        String root = meta.cyWebMenuItem.root.strip().toLowerCase(Locale.ROOT);
        return DESKTOP_MENUS.stream()
                .filter(menu -> menu.toLowerCase(Locale.ROOT).equals(root))
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
