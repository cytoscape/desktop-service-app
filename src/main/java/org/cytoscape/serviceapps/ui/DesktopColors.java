package org.cytoscape.serviceapps.ui;

import java.awt.Color;

import javax.swing.UIManager;

/**
 * Desktop's theme colors, as {@code LookAndFeelUtil} and Desktop's own links read them. Each falls
 * back to a close standard color outside Cytoscape, such as in tests.
 */
public final class DesktopColors {

    static final Color FALLBACK_LINK = new Color(0x1a, 0x5f, 0xb4);
    static final Color FALLBACK_SUCCESS = new Color(0x1b, 0x80, 0x3a);
    static final Color FALLBACK_ERROR = new Color(0xc6, 0x28, 0x28);

    private DesktopColors() {}

    /** Link text, as on Desktop's starter panel. */
    public static Color link() {
        return theme("CyColor.complement(+1)", FALLBACK_LINK);
    }

    /** {@code LookAndFeelUtil.getSuccessColor()}. */
    public static Color success() {
        return theme("CyColor.secondary1(-1)", FALLBACK_SUCCESS);
    }

    /** {@code LookAndFeelUtil.getErrorColor()}. */
    public static Color error() {
        return theme("CyColor.secondary2(-1)", FALLBACK_ERROR);
    }

    private static Color theme(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }
}
