package org.cytoscape.serviceapps.manager;

import java.awt.Component;

import javax.swing.JOptionPane;

/** Asks the user to confirm something. Injected so the dialog can be tested without a person. */
@FunctionalInterface
public interface Confirmer {

    boolean confirm(Component parent, String title, String question);

    /** Shows a confirm dialog and returns the option chosen, as {@link JOptionPane} does. */
    @FunctionalInterface
    interface ConfirmDialog {
        int show(Component parent, Object message, String title, int optionType, int messageType);
    }

    /** A Yes/No question shown by {@code dialog}, confirmed only by Yes. */
    static Confirmer optionPane(ConfirmDialog dialog) {
        return (parent, title, question) ->
                dialog.show(
                                parent,
                                question,
                                title,
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.QUESTION_MESSAGE)
                        == JOptionPane.YES_OPTION;
    }

    /** A Yes/No option pane. */
    Confirmer OPTION_PANE = optionPane(JOptionPane::showConfirmDialog);
}
