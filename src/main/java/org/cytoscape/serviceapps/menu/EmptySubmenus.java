package org.cytoscape.serviceapps.menu;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cytoscape.application.swing.AbstractCyAction;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.serviceapps.placement.MenuPlacement;

/**
 * Removes the submenus that a removed menu entry leaves empty.
 *
 * <p>Desktop's menu bar removes an emptied submenu only one level up, and looks it up by the
 * entry's raw preferred menu. A service app's preferred menu carries submenu gravities in brackets
 * ({@code Tools.Example Service App[0.0]}), while Desktop's menu tracker keys submenus by the plain
 * path ({@code Tools.Example Service App}), so the lookup misses and the empty submenu stays.
 *
 * <p>This lets Desktop clean up its own state: for each submenu on the path, innermost first, it
 * adds a placeholder action at the plain path and removes it again. Desktop's {@code removeAction}
 * then purges the submenu, by the key that matches, if nothing else is in it. A submenu still in
 * use is left alone, a missing one is created and purged again, and nothing is hidden, so the worst
 * case is the empty submenu Desktop leaves today.
 */
public class EmptySubmenus {

    /** Does nothing, for wiring without Desktop's menus. */
    public static final EmptySubmenus NONE = new EmptySubmenus(null);

    /** The placeholder's name, unique so no menu customization applies to it. */
    static final String PLACEHOLDER = "Desktop Service App submenu cleanup";

    private static final Logger LOGGER = LoggerFactory.getLogger(EmptySubmenus.class);

    private final CySwingApplication swing;

    public EmptySubmenus(CySwingApplication swing) {
        this.swing = swing;
    }

    /**
     * Removes the submenus of {@code placement} that are empty, once Desktop has removed the entry.
     * Desktop queues that removal on the EDT; this is queued behind it.
     */
    public void purge(String appName, MenuPlacement placement) {
        if (swing == null || placement.submenus().isEmpty()) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            purgeNow(appName, placement);
        } else {
            SwingUtilities.invokeLater(() -> purgeNow(appName, placement));
        }
    }

    /**
     * Purges on the calling thread, which must be the EDT.
     *
     * @return the submenus on the path still in the menu bar, as {@code Apps > Fixture}
     */
    List<String> purgeNow(String appName, MenuPlacement placement) {
        List<String> paths = plainPaths(placement);
        for (int i = paths.size() - 1; i >= 0; i--) {
            Placeholder placeholder = new Placeholder(paths.get(i));
            swing.addAction(placeholder);
            swing.removeAction(placeholder);
        }
        List<String> kept = shown(placement);
        if (kept.isEmpty()) {
            LOGGER.info(
                    "Empty submenus removed for '{}': {}",
                    appName,
                    String.join(", ", described(placement)));
        } else {
            LOGGER.info(
                    "Submenus kept for '{}', still in use: {}", appName, String.join(", ", kept));
        }
        return kept;
    }

    /** {@code Tools.A} then {@code Tools.A.B}: each submenu's plain path, outermost first. */
    private static List<String> plainPaths(MenuPlacement placement) {
        List<String> paths = new ArrayList<>();
        StringBuilder path = new StringBuilder(placement.topMenu());
        for (String submenu : placement.submenus()) {
            path.append('.').append(submenu);
            paths.add(path.toString());
        }
        return paths;
    }

    private static List<String> described(MenuPlacement placement) {
        List<String> names = new ArrayList<>();
        StringBuilder path = new StringBuilder(placement.topMenu());
        for (String submenu : placement.submenus()) {
            path.append(" > ").append(submenu);
            names.add(path.toString());
        }
        return names;
    }

    /** The path's submenus attached to the menu bar, outermost first; reads only. */
    private List<String> shown(MenuPlacement placement) {
        List<String> shown = new ArrayList<>();
        JMenuBar bar = swing.getJMenuBar();
        JMenu menu = null;
        for (int i = 0; bar != null && i < bar.getMenuCount(); i++) {
            JMenu top = bar.getMenu(i);
            if (top != null && placement.topMenu().equals(top.getText())) {
                menu = top;
            }
        }
        List<String> names = described(placement);
        for (int level = 0; menu != null && level < placement.submenus().size(); level++) {
            menu = child(menu, placement.submenus().get(level));
            if (menu != null) {
                shown.add(names.get(level));
            }
        }
        return shown;
    }

    private static JMenu child(JMenu parent, String name) {
        for (Component component : parent.getMenuComponents()) {
            if (component instanceof JMenu menu && name.equals(menu.getText())) {
                return menu;
            }
        }
        return null;
    }

    /** Added and removed at once, so Desktop purges the submenu it was in if that is now empty. */
    private static final class Placeholder extends AbstractCyAction {
        Placeholder(String preferredMenu) {
            super(PLACEHOLDER);
            setPreferredMenu(preferredMenu);
        }

        @Override
        public void actionPerformed(ActionEvent e) {}
    }
}
