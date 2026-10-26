package org.cytoscape.serviceapps.ui;

import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Text from service metadata is never read as HTML. */
class PlainTextTest {

    /** Makes Desktop fetch a remote image if Swing reads it as HTML. */
    static final String PAYLOAD = "<html><img src='http://127.0.0.1:9/x.png'>Gap</html>";

    @Test
    void aMarkedLabelShowsHtmlAsText() {
        JLabel plain = PlainText.of(new JLabel());
        plain.setText(PAYLOAD);
        JLabel unmarked = new JLabel(PAYLOAD);

        assertNull(plain.getClientProperty(BasicHTML.propertyKey), "no HTML view is built");
        assertEquals(PAYLOAD, plain.getText());
        assertNotNull(unmarked.getClientProperty(BasicHTML.propertyKey), "what it guards against");
    }

    @Test
    void tooltipsEscapeTheText() {
        assertEquals(
                "<html>&lt;html&gt;&lt;img src='http://127.0.0.1:9/x.png'&gt;Gap&lt;/html&gt;"
                        + "</html>",
                PlainText.tooltip(PAYLOAD));
        assertEquals("<html>a &amp; b</html>", PlainText.tooltip("a & b"));
        assertEquals(
                "<html><div style='width:100px'>&lt;b&gt;</div></html>",
                PlainText.tooltip("<b>", 100));
        assertNull(PlainText.tooltip(null));
        assertNull(PlainText.tooltip(" "));
        assertNull(PlainText.tooltip(" ", 100));
    }

    @Test
    void literalChangesOnlyTextSwingWouldReadAsHtml() {
        assertEquals("‹html><b>x</b>", PlainText.literal("<html><b>x</b>"));
        assertEquals("‹HTML>x", PlainText.literal("<HTML>x"));
        assertEquals("p < 0.05 <html>", PlainText.literal("p < 0.05 <html>"));
        assertEquals("<b>x</b>", PlainText.literal("<b>x</b>"), "Swing reads only <html> as HTML");
        assertNull(PlainText.literal(null));
    }
}
