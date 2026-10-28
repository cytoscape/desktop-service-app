package org.cytoscape.serviceapps.manager;

import java.awt.Component;

import javax.swing.JOptionPane;

/** Asks the user to confirm something. Injected so the dialog can be tested without a person. */
@FunctionalInterface
public interface Confirmer {

    boolean confirm(Component parent, String title, String question);

    /** A Yes/No option pane. */
    Confirmer OPTION_PANE =
            (parent, title, question) ->
                    JOptionPane.showConfirmDialog(
                                    parent,
                                    question,
                                    title,
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.QUESTION_MESSAGE)
                            == JOptionPane.YES_OPTION;
}
