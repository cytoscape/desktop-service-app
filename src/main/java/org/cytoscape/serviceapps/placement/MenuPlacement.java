package org.cytoscape.serviceapps.placement;

import java.util.List;

/**
 * Where a service app's menu entry goes.
 *
 * @param topMenu the Desktop top-level menu, spelled as Desktop spells it
 * @param submenus the submenus under it, outermost first
 * @param preferredMenu the value for Desktop's {@code PREFERRED_MENU}: the menu path with each
 *     submenu's gravity in brackets, such as {@code Apps.Fixture[10.0]}
 * @param title the menu item's text
 * @param gravity the menu item's position; lower sorts higher
 * @param fallbackReason why the app went under Apps &gt; Service Apps instead of the menu it named,
 *     or null when it got the menu it named
 */
public record MenuPlacement(
        String topMenu,
        List<String> submenus,
        String preferredMenu,
        String title,
        double gravity,
        String fallbackReason) {

    /** The path for logs and messages, such as {@code Apps > Fixture > Hello}. */
    public String describe() {
        StringBuilder text = new StringBuilder(topMenu);
        for (String submenu : submenus) {
            text.append(" > ").append(submenu);
        }
        return text.append(" > ").append(title).toString();
    }
}
