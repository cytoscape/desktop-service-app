package org.cytoscape.serviceapps.testsupport;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import org.cytoscape.application.swing.AbstractCyAction;
import org.cytoscape.application.swing.CyAction;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.internal.view.CytoscapeMenuBar;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.TaskFactory;

import static org.cytoscape.work.ServiceProperties.MENU_GRAVITY;
import static org.cytoscape.work.ServiceProperties.PREFERRED_MENU;
import static org.cytoscape.work.ServiceProperties.TITLE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Desktop's real menu bar ({@code CytoscapeMenuBar} from swing-application-impl), reached the way
 * Desktop reaches it.
 *
 * <ul>
 *   <li>{@link #swing()}: {@code CySwingApplication}'s menu methods, which run on the EDT, at once
 *       when called there and queued otherwise, as {@code CytoscapeMenus} does.
 *   <li>{@link #registrar()}: registering a menu {@code TaskFactory} adds an action built from its
 *       properties, and unregistering removes it, as {@code CytoscapeMenuPopulator} does.
 * </ul>
 */
public final class DesktopMenus {

    /** Desktop's top-level menus, which always exist. */
    public static final List<String> TOP_MENUS =
            List.of("File", "Edit", "View", "Select", "Layout", "Apps", "Tools", "Help");

    private final CytoscapeMenuBar menuBar;
    private final CySwingApplication swing = mock(CySwingApplication.class);
    private final CyServiceRegistrar registrar = mock(CyServiceRegistrar.class);
    private final Map<Object, CyAction> entries = new IdentityHashMap<>();

    public DesktopMenus() {
        CytoscapeMenuBar[] bar = new CytoscapeMenuBar[1];
        onEdtAndWait(
                () -> {
                    bar[0] = new CytoscapeMenuBar(mock(CyServiceRegistrar.class));
                    TOP_MENUS.forEach(bar[0]::getMenu);
                });
        menuBar = bar[0];

        doAnswer(call -> onEdt(() -> menuBar.addAction(call.getArgument(0))))
                .when(swing)
                .addAction(any());
        doAnswer(call -> onEdt(() -> menuBar.removeAction(call.getArgument(0))))
                .when(swing)
                .removeAction(any());
        when(swing.getJMenuBar()).thenReturn(menuBar);
        when(swing.getJMenu(anyString())).thenAnswer(call -> menuBar.getMenu(call.getArgument(0)));

        doAnswer(
                        call -> {
                            Properties props = call.getArgument(2);
                            CyAction action =
                                    item(
                                            props.getProperty(PREFERRED_MENU),
                                            props.getProperty(TITLE),
                                            Float.parseFloat(props.getProperty(MENU_GRAVITY)));
                            entries.put(call.getArgument(0), action);
                            swing.addAction(action);
                            return null;
                        })
                .when(registrar)
                .registerService(any(), eq(TaskFactory.class), any(Properties.class));
        doAnswer(
                        call -> {
                            CyAction action = entries.remove(call.getArgument(0));
                            if (action != null) {
                                swing.removeAction(action);
                            }
                            return null;
                        })
                .when(registrar)
                .unregisterService(any(), eq(TaskFactory.class));
    }

    public CySwingApplication swing() {
        return swing;
    }

    public CyServiceRegistrar registrar() {
        return registrar;
    }

    /** A plain menu item, such as another app's, at {@code preferredMenu}. */
    public static CyAction item(String preferredMenu, String title, float gravity) {
        AbstractCyAction action =
                new AbstractCyAction(title) {
                    @Override
                    public void actionPerformed(ActionEvent e) {}
                };
        action.setPreferredMenu(preferredMenu);
        action.setMenuGravity(gravity);
        return action;
    }

    /**
     * Every menu item and submenu attached to the menu bar under {@code topMenu}, in display order,
     * as {@code Tools > Example > Item}; an empty submenu is listed on its own.
     */
    public List<String> tree(String topMenu) {
        List<String> lines = new ArrayList<>();
        onEdtAndWait(
                () -> {
                    for (int i = 0; i < menuBar.getMenuCount(); i++) {
                        JMenu top = menuBar.getMenu(i);
                        if (top != null && top.getText().equals(topMenu)) {
                            walk(top, topMenu, lines);
                        }
                    }
                });
        return lines;
    }

    private static void walk(JMenu menu, String path, List<String> lines) {
        for (Component component : menu.getMenuComponents()) {
            if (component instanceof JMenu submenu) {
                String inner = path + " > " + submenu.getText();
                int before = lines.size();
                walk(submenu, inner, lines);
                if (lines.size() == before) {
                    lines.add(inner + " (empty)");
                }
            } else if (component instanceof JMenuItem item) {
                lines.add(path + " > " + item.getText() + (item.isVisible() ? "" : " (hidden)"));
            }
        }
    }

    /** Lets work queued on the EDT finish. */
    public static void flush() {
        onEdtAndWait(() -> {});
    }

    private static Object onEdt(Runnable work) {
        if (SwingUtilities.isEventDispatchThread()) {
            work.run();
        } else {
            SwingUtilities.invokeLater(work);
        }
        return null;
    }

    public static void onEdtAndWait(Runnable work) {
        if (SwingUtilities.isEventDispatchThread()) {
            work.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(work);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
