package org.cytoscape.serviceapps.form;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.cytoscape.serviceapps.ui.DesktopColors;

/**
 * Draws a {@link FormSpec} as Swing controls and reads the entered values back.
 *
 * <p>Parameters with {@code groups} are drawn inside titled sections, nested by group (see {@link
 * ParameterGroups}).
 *
 * <p>Each control is named for tests: {@code param:<key>} for a parameter, {@code column:<key>} for
 * an input column, and {@code error:<key>} for its inline validation message. Radio buttons are
 * named {@code param:<key>:<option>}, and a group's section {@code group:<A/B>}.
 */
public class FormPanel extends JPanel {

    static final String UNDO_NOTE = "Results are applied to your network and can't be undone.";

    private final FormSpec spec;
    private final Map<String, Supplier<List<String>>> parameterReaders = new LinkedHashMap<>();
    private final Map<String, Supplier<List<String>>> columnReaders = new LinkedHashMap<>();
    private final Map<String, JLabel> errorLabels = new LinkedHashMap<>();

    public FormPanel(FormSpec spec) {
        super(new GridBagLayout());
        this.spec = spec;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        int row = 0;
        row = header(row);
        if (!spec.parameters().isEmpty()) {
            row = section("Parameters", row);
            row = parameters(this, ParameterGroups.of(spec.parameters()), row);
        }
        if (!spec.inputColumns().isEmpty()) {
            row = section("Input columns", row);
            for (ControlSpec control : spec.inputColumns()) {
                row = control(this, control, "column:", columnReaders, row);
            }
        }
        JLabel note = new JLabel(UNDO_NOTE);
        note.setName("undoNote");
        add(note, constraints(0, row, 2, 0, new Insets(12, 0, 0, 0)));
        refreshErrors();
    }

    /** The values currently entered. */
    public FormValues values() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        parameterReaders.forEach((key, reader) -> params.put(key, reader.get()));
        Map<String, List<String>> columns = new LinkedHashMap<>();
        columnReaders.forEach((key, reader) -> columns.put(key, reader.get()));
        return new FormValues(params, columns);
    }

    /** Updates every inline message from the current values. */
    public void refreshErrors() {
        FormValues values = values();
        for (ControlSpec control : spec.parameters()) {
            show(control, control.validate(values.parameters().get(control.key())));
        }
        for (ControlSpec control : spec.inputColumns()) {
            show(control, control.validate(values.inputColumns().get(control.key())));
        }
    }

    private void show(ControlSpec control, Optional<String> problem) {
        JLabel label = errorLabels.get(control.key());
        if (label != null) {
            label.setText(problem.orElse(""));
            label.setVisible(problem.isPresent());
        }
    }

    private int header(int row) {
        JLabel title =
                new JLabel(
                        "<html><b>"
                                + escape(spec.appName())
                                + "</b> &nbsp;version "
                                + escape(spec.version())
                                + "</html>");
        title.setName("title");
        add(title, constraints(0, row++, 2, 0, new Insets(0, 0, 4, 0)));
        if (spec.citation() != null) {
            JLabel citation = new JLabel("Citation: " + spec.citation());
            citation.setName("citation");
            add(citation, constraints(0, row++, 2, 0, new Insets(0, 0, 4, 0)));
        }
        if (spec.description() != null) {
            JLabel description =
                    new JLabel(
                            "<html><div style='width:360px'>"
                                    + escape(spec.description())
                                    + "</div></html>");
            description.setName("description");
            add(description, constraints(0, row++, 2, 0, new Insets(4, 0, 4, 0)));
        }
        return row;
    }

    private int section(String name, int row) {
        JLabel label = new JLabel("<html><b>" + name + "</b></html>");
        add(label, constraints(0, row, 2, 0, new Insets(10, 0, 4, 0)));
        return row + 1;
    }

    /** Adds a group's controls and subgroups to {@code target}, in order, from {@code row}. */
    private int parameters(JPanel target, ParameterGroups.Group group, int row) {
        for (ParameterGroups.Child child : group.children()) {
            if (child instanceof ParameterGroups.Control control) {
                row = control(target, control.spec(), "param:", parameterReaders, row);
            } else if (child instanceof ParameterGroups.Group subgroup) {
                JPanel section = new JPanel(new GridBagLayout());
                section.setName("group:" + String.join("/", subgroup.path()));
                section.setBorder(
                        BorderFactory.createCompoundBorder(
                                BorderFactory.createTitledBorder(subgroup.name()),
                                BorderFactory.createEmptyBorder(2, 6, 4, 6)));
                parameters(section, subgroup, 0);
                target.add(section, constraints(0, row++, 2, 1, new Insets(6, 0, 4, 0)));
            }
        }
        return row;
    }

    private int control(
            JPanel target,
            ControlSpec control,
            String prefix,
            Map<String, Supplier<List<String>>> readers,
            int row) {
        JLabel label = new JLabel(control.label());
        label.setToolTipText(control.tooltip());
        JComponent component = component(control, prefix, readers);
        component.setToolTipText(control.tooltip());
        target.add(label, constraints(0, row, 1, 0, new Insets(2, 0, 2, 8)));
        target.add(component, constraints(1, row, 1, 1, new Insets(2, 0, 2, 0)));
        JLabel error = new JLabel();
        error.setName("error:" + control.key());
        error.setForeground(DesktopColors.error());
        errorLabels.put(control.key(), error);
        target.add(error, constraints(1, row + 1, 1, 1, new Insets(0, 0, 4, 0)));
        return row + 2;
    }

    private JComponent component(
            ControlSpec control, String prefix, Map<String, Supplier<List<String>>> readers) {
        String name = prefix + control.key();
        switch (control.kind()) {
            case TEXT:
                {
                    JTextField field = new JTextField(first(control.selected()), 20);
                    field.setName(name);
                    field.getDocument().addDocumentListener(onChange());
                    readers.put(control.key(), () -> List.of(field.getText()));
                    return field;
                }
            case CHECK_BOX:
                {
                    JCheckBox box = new JCheckBox();
                    box.setName(name);
                    box.setSelected("true".equals(first(control.selected())));
                    box.addActionListener(e -> refreshErrors());
                    readers.put(control.key(), () -> List.of(Boolean.toString(box.isSelected())));
                    return box;
                }
            case RADIO:
                {
                    JPanel group = new JPanel();
                    group.setLayout(new BoxLayout(group, BoxLayout.X_AXIS));
                    group.setName(name);
                    ButtonGroup buttons = new ButtonGroup();
                    List<JRadioButton> radios = new ArrayList<>();
                    for (String option : control.options()) {
                        JRadioButton radio = new JRadioButton(option);
                        radio.setName(name + ":" + option);
                        radio.setSelected(control.selected().contains(option));
                        radio.addActionListener(e -> refreshErrors());
                        buttons.add(radio);
                        group.add(radio);
                        radios.add(radio);
                    }
                    readers.put(
                            control.key(),
                            () ->
                                    radios.stream()
                                            .filter(JRadioButton::isSelected)
                                            .map(JRadioButton::getText)
                                            .toList());
                    return group;
                }
            case INPUT_COLUMN:
                if (control.multiSelect()) {
                    JList<String> list = new JList<>(control.options().toArray(new String[0]));
                    list.setName(name);
                    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                    list.setVisibleRowCount(Math.min(6, Math.max(3, control.options().size())));
                    List<Integer> selected = new ArrayList<>();
                    for (String value : control.selected()) {
                        int index = control.options().indexOf(value);
                        if (index >= 0) {
                            selected.add(index);
                        }
                    }
                    list.setSelectedIndices(
                            selected.stream().mapToInt(Integer::intValue).toArray());
                    list.addListSelectionListener(e -> refreshErrors());
                    readers.put(control.key(), list::getSelectedValuesList);
                    JScrollPane scroll = new JScrollPane(list);
                    scroll.setName(name + ":scroll");
                    return scroll;
                }
                // single-select input columns are drop-downs, like the other column pickers
            default:
                {
                    JComboBox<String> combo =
                            new JComboBox<>(control.options().toArray(new String[0]));
                    combo.setName(name);
                    combo.setSelectedItem(
                            control.selected().isEmpty() ? null : control.selected().get(0));
                    if (control.selected().isEmpty()) {
                        combo.setSelectedIndex(-1);
                    }
                    combo.addActionListener(e -> refreshErrors());
                    readers.put(
                            control.key(),
                            () ->
                                    combo.getSelectedItem() == null
                                            ? List.of()
                                            : List.of((String) combo.getSelectedItem()));
                    return combo;
                }
        }
    }

    private DocumentListener onChange() {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshErrors();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshErrors();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshErrors();
            }
        };
    }

    private static GridBagConstraints constraints(
            int x, int y, int width, double weightX, Insets insets) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.weightx = weightX;
        c.anchor = GridBagConstraints.WEST;
        c.fill = weightX > 0 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        c.insets = insets;
        return c;
    }

    private static String first(List<String> values) {
        return values.isEmpty() ? "" : values.get(0);
    }

    private static String escape(String text) {
        return text == null
                ? ""
                : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Finds a named descendant, for tests. */
    public Component find(String name) {
        return find(this, name);
    }

    private static Component find(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof Container nested) {
                Component found = find(nested, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
