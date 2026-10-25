package org.cytoscape.serviceapps.ui;

import javax.swing.JComponent;

/**
 * Shows text from a service app's metadata as literal text. Swing reads any label, button or
 * tooltip text that starts with {@code <html>} as HTML, and HTML can make Desktop fetch a remote
 * image when it is shown, so a service could otherwise trigger requests from the user's machine.
 */
public final class PlainText {

    /** Swing's client property that stops a component reading its text as HTML. */
    public static final String HTML_DISABLE = "html.disable";

    /** The prefix that makes Swing read text as HTML. */
    private static final String HTML = "<html>";

    private PlainText() {}

    /**
     * Stops {@code component} reading its text as HTML. Call it before the text is set: Swing reads
     * the text when it is set.
     */
    public static <T extends JComponent> T of(T component) {
        component.putClientProperty(HTML_DISABLE, Boolean.TRUE);
        return component;
    }

    /** A tooltip showing {@code text} as written, or null when there is none. */
    public static String tooltip(String text) {
        return text == null || text.isBlank() ? null : "<html>" + escape(text) + "</html>";
    }

    /** As {@link #tooltip(String)}, wrapped at {@code width} pixels for long text. */
    public static String tooltip(String text, int width) {
        return text == null || text.isBlank()
                ? null
                : "<html><div style='width:" + width + "px'>" + escape(text) + "</div></html>";
    }

    /**
     * The text, changed only if Swing would read it as HTML: its first {@code <} becomes {@code ‹}.
     * For text set on components this code doesn't create, such as Desktop's menu items.
     */
    public static String literal(String text) {
        return text != null && text.regionMatches(true, 0, HTML, 0, HTML.length())
                ? "\u2039" + text.substring(1)
                : text;
    }

    /** The text with HTML's special characters escaped, for use inside HTML. */
    public static String escape(String text) {
        return text == null
                ? ""
                : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
