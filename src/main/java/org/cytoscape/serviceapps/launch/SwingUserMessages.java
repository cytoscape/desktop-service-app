package org.cytoscape.serviceapps.launch;

import java.awt.Component;
import java.util.function.Supplier;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Shows messages in a dialog over Desktop's main window. */
public class SwingUserMessages implements UserMessages {

    private final Supplier<Component> parent;

    public SwingUserMessages(Supplier<Component> parent) {
        this.parent = parent;
    }

    @Override
    public void warn(String title, String message) {
        SwingUtilities.invokeLater(
                () ->
                        JOptionPane.showMessageDialog(
                                parent.get(), message, title, JOptionPane.WARNING_MESSAGE));
    }
}
