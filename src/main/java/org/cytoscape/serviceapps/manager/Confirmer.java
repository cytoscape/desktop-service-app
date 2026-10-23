package org.cytoscape.serviceapps.manager;

import java.awt.Component;

import javax.swing.JOptionPane;

/** Asks the user to confirm something. Injected so the dialog can be tested without a person. */
@FunctionalInterface
public interface Confirmer {

    boolean confirm(Component parent, String title, String question);

    /** An OK/Cancel option pane. */
    Confirmer OPTION_PANE =
            (parent, title, question) ->
                    JOptionPane.showConfirmDialog(
                                    parent,
                                    question,
                                    title,
                                    JOptionPane.OK_CANCEL_OPTION,
                                    JOptionPane.QUESTION_MESSAGE)
                            == JOptionPane.OK_OPTION;
}
