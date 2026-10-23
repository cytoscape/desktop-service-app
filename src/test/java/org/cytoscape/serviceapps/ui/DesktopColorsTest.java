package org.cytoscape.serviceapps.ui;

import java.awt.Color;

import javax.swing.UIManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Desktop's theme colors when Desktop sets them, and the fallbacks when it doesn't. */
class DesktopColorsTest {

    private static final String[] KEYS = {
        "CyColor.complement(+1)", "CyColor.secondary1(-1)", "CyColor.secondary2(-1)"
    };

    @AfterEach
    void clear() {
        for (String key : KEYS) {
            UIManager.put(key, null);
        }
    }

    @Test
    void outsideDesktopTheFallbacksAreUsed() {
        assertEquals(DesktopColors.FALLBACK_LINK, DesktopColors.link());
        assertEquals(DesktopColors.FALLBACK_SUCCESS, DesktopColors.success());
        assertEquals(DesktopColors.FALLBACK_ERROR, DesktopColors.error());
    }

    @Test
    void desktopsThemeColorsWin() {
        UIManager.put(KEYS[0], Color.BLUE);
        UIManager.put(KEYS[1], Color.GREEN);
        UIManager.put(KEYS[2], Color.RED);

        assertEquals(Color.BLUE, DesktopColors.link());
        assertEquals(Color.GREEN, DesktopColors.success());
        assertEquals(Color.RED, DesktopColors.error());
    }
}
