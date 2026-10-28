package org.cytoscape.serviceapps.manager;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmerTest {

    @Test
    void asksAYesNoQuestionWithTheTitleAndQuestionGiven() {
        List<Object> asked = new ArrayList<>();
        Confirmer confirmer =
                Confirmer.optionPane(
                        (parent, message, title, optionType, messageType) -> {
                            asked.addAll(List.of(message, title, optionType, messageType));
                            return JOptionPane.YES_OPTION;
                        });

        confirmer.confirm(null, "Uninstall", "Are you sure?");

        assertEquals(
                List.of(
                        "Are you sure?",
                        "Uninstall",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE),
                asked);
    }

    @Test
    void onlyYesConfirms() {
        assertTrue(answering(JOptionPane.YES_OPTION));
        assertFalse(answering(JOptionPane.NO_OPTION));
        assertFalse(answering(JOptionPane.CLOSED_OPTION), "closing the pane counts as No");
    }

    private static boolean answering(int option) {
        return Confirmer.optionPane((parent, message, title, optionType, messageType) -> option)
                .confirm(null, "Uninstall", "Are you sure?");
    }
}
