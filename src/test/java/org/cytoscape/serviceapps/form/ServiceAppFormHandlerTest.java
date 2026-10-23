package org.cytoscape.serviceapps.form;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.serviceapps.testsupport.TestNetworks;
import org.cytoscape.work.Tunable;
import org.cytoscape.work.swing.GUITunableHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The registered handler draws each control from the form spec and writes the entered values back.
 * Needs a display; CI runs it under xvfb.
 */
class ServiceAppFormHandlerTest {

    /** A task-like holder, as Desktop's Tunables framework sees one. */
    public static class Holder {
        @Tunable(description = "Service app", context = "gui")
        public ServiceAppForm form;
    }

    private final CyNetwork network = TestNetworks.withEveryColumnType();

    @BeforeAll
    static void needsADisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display; CI runs it under xvfb");
    }

    private ServiceAppFormHandler handler(String scenario) throws Exception {
        Holder holder = new Holder();
        holder.form =
                new ServiceAppForm(
                        new FormSpecBuilder()
                                .build(TestNetworks.scenario(scenario), new JsonObject(), network));
        Field field = Holder.class.getField("form");
        GUITunableHandler created =
                onEdt(
                        () ->
                                new ServiceAppFormHandlerFactory()
                                        .createTunableHandler(
                                                field, holder, field.getAnnotation(Tunable.class)));
        return (ServiceAppFormHandler) created;
    }

    private static <T> T onEdt(Callable<T> work) throws Exception {
        List<T> result = new ArrayList<>(1);
        List<Exception> failure = new ArrayList<>(1);
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        result.add(work.call());
                    } catch (Exception e) {
                        failure.add(e);
                    }
                });
        if (!failure.isEmpty()) {
            throw failure.get(0);
        }
        return result.get(0);
    }

    @Test
    void theFactoryOnlyHandlesServiceAppForms() throws Exception {
        Field other = ServiceAppFormHandlerTest.class.getDeclaredField("network");

        assertNull(new ServiceAppFormHandlerFactory().createTunableHandler(other, this, null));
    }

    @Test
    void textIsATextFieldWithTheDefaultAndInlineValidation() throws Exception {
        ServiceAppFormHandler handler = handler("param-text-string");
        FormPanel panel = handler.formPanel();

        JTextField field = assertInstanceOf(JTextField.class, panel.find("param:Algorithm"));
        assertEquals("louvain", field.getText());
        assertEquals("Help text for Algorithm", field.getToolTipText());
        JLabel error = (JLabel) panel.find("error:Algorithm");
        assertFalse(error.isVisible());

        onEdt(
                () -> {
                    field.setText("nope");
                    return null;
                });
        assertTrue(error.isVisible());
        assertEquals("Must be louvain or leiden", error.getText());
    }

    @Test
    void dropDownHasTheValuesInOrderAndTheDefaultSelected() throws Exception {
        FormPanel panel = handler("param-dropdown").formPanel();

        JComboBox<?> combo = assertInstanceOf(JComboBox.class, panel.find("param:Method"));
        assertEquals(3, combo.getItemCount());
        assertEquals("louvain", combo.getItemAt(0));
        assertEquals("infomap", combo.getItemAt(2));
        assertEquals("leiden", combo.getSelectedItem());
        assertNull(
                ((JComboBox<?>)
                                handler("param-dropdown-no-default")
                                        .formPanel()
                                        .find("param:Method"))
                        .getSelectedItem());
    }

    @Test
    void radioIsOneButtonPerValueWithTheDefaultSelected() throws Exception {
        FormPanel panel = handler("param-radio").formPanel();

        JRadioButton up = (JRadioButton) panel.find("param:Direction:up");
        JRadioButton down = (JRadioButton) panel.find("param:Direction:down");
        assertFalse(up.isSelected());
        assertTrue(down.isSelected());
        FormPanel none = handler("param-radio-no-default").formPanel();
        assertFalse(((JRadioButton) none.find("param:Direction:up")).isSelected());
        assertFalse(((JRadioButton) none.find("param:Direction:down")).isSelected());
        assertTrue(((JLabel) none.find("error:Direction")).isVisible());
    }

    @Test
    void checkBoxIsCheckedOnlyForATrueDefault() throws Exception {
        assertTrue(
                ((JCheckBox) handler("param-checkbox-true").formPanel().find("param:Weighted"))
                        .isSelected());
        assertFalse(
                ((JCheckBox) handler("param-checkbox-false").formPanel().find("param:Weighted"))
                        .isSelected());
    }

    @Test
    void columnPickersListTheirTablesColumns() throws Exception {
        FormPanel nodes = handler("param-node-column-filters").formPanel();
        FormPanel edges = handler("param-edge-column-filters").formPanel();

        JComboBox<?> nodeDoubles = (JComboBox<?>) nodes.find("param:nodeColumn double");
        assertEquals(1, nodeDoubles.getItemCount());
        assertEquals("c_double", nodeDoubles.getItemAt(0));
        JComboBox<?> edgeAny = (JComboBox<?>) edges.find("param:edgeColumn any");
        for (int i = 0; i < edgeAny.getItemCount(); i++) {
            assertFalse(String.valueOf(edgeAny.getItemAt(i)).startsWith("c_"));
        }
        assertEquals(
                "name", ((JComboBox<?>) nodes.find("param:nodeColumn string")).getSelectedItem());
    }

    @Test
    void inputColumnsAreSingleOrMultiSelectPickers() throws Exception {
        FormPanel panel = handler("input-columns").formPanel();

        JComboBox<?> gene = assertInstanceOf(JComboBox.class, panel.find("column:Gene"));
        assertEquals("name", gene.getSelectedItem());
        JScrollPane scroll = (JScrollPane) panel.find("column:Scores:scroll");
        JList<?> scores = (JList<?>) scroll.getViewport().getView();
        assertEquals(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION, scores.getSelectionMode());
        assertNull(panel.find("column:shared name"));
    }

    @Test
    void theHeaderShowsNameVersionCitationAndOptionalDescription() throws Exception {
        FormPanel shown = handler("description-shown").formPanel();
        FormPanel hidden = handler("description-hidden").formPanel();

        assertTrue(((JLabel) shown.find("title")).getText().contains("version 1.0"));
        assertEquals("Citation: NA", ((JLabel) shown.find("citation")).getText());
        assertNotNull(shown.find("description"));
        assertNull(hidden.find("description"));
        assertEquals(FormPanel.UNDO_NOTE, ((JLabel) shown.find("undoNote")).getText());
    }

    @Test
    void handleWritesTheEnteredValuesBack() throws Exception {
        ServiceAppFormHandler handler = handler("param-checkbox-true");
        Holder holder = new Holder();
        Component box = handler.formPanel().find("param:Weighted");

        onEdt(
                () -> {
                    ((JCheckBox) box).setSelected(false);
                    handler.handle();
                    return null;
                });

        FormValues values = handler.formPanel().values();
        assertEquals(List.of("false"), values.parameters().get("Weighted"));
        assertNull(holder.form);
    }
}
