package org.cytoscape.serviceapps.model;

import java.util.List;

/**
 * Where the service asks for its menu entry.
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/cyWebMenuItem">cyWebMenuItem</a>
 */
public class CyWebMenuItem {

    public String root;
    public List<PathElement> path;

    public List<PathElement> path() {
        return path == null ? List.of() : path;
    }

    /** One submenu or the final menu item. */
    public static class PathElement {
        public String name;

        /** Lower sorts higher in the menu. */
        public Double gravity;
    }
}
