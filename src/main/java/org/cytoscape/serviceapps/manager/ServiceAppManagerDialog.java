package org.cytoscape.serviceapps.manager;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import org.cytoscape.serviceapps.ui.DesktopColors;
import org.cytoscape.serviceapps.ui.PlainText;
import org.cytoscape.util.swing.IconManager;

/**
 * The Service Apps dialog: every installed app's name and status, or a centered "No Service Apps
 * installed" message. Non-modal; renders {@link ServiceAppManagerModel} and closes with Close, the
 * window's X or Escape.
 *
 * <ul>
 *   <li>Each row ends with a trashcan. A click on it, or Delete or Backspace on the selected row,
 *       asks {@code Are you sure you want to uninstall <name>?}, and Yes uninstalls the app.
 *   <li>A single click selects a row and shades it light grey.
 *   <li>A double click on an app that is Online and runnable, or Enter on its selected row, invokes
 *       it: the row turns light blue while its input form is open. On any other app, both do
 *       nothing. A double click on the trashcan only asks to uninstall.
 *   <li>A click anywhere else in the dialog clears the selection.
 * </ul>
 */
public class ServiceAppManagerDialog extends JDialog {

    public static final String TITLE = "Service Apps";
    public static final String EMPTY_TEXT = "No Service Apps installed";

    /** Points users who already know a service app's URL to the install command. */
    public static final String COMMAND_HINT =
            "If you know a service app's URL, install it from the Command Line"
                    + " (View > Show Command Panel):";

    public static final String INSTALL_COMMAND = "serviceapps install url=<service URL>";

    static final int NAME = 0;
    static final int DESCRIPTION = 1;
    static final int AUTHOR = 2;
    static final int STATUS = 3;
    static final int TRASH = 4;

    /** The size of the trashcan glyph, in points. */
    public static final float ICON_SIZE = 14.0f;

    /** The selected row, which Enter runs and Delete or Backspace offers to uninstall. */
    static final Color SELECTED_BACKGROUND = new Color(0xee, 0xee, 0xee);

    /** The double-clicked row, while its input form is open. */
    static final Color INVOKING_BACKGROUND = new Color(0xe1, 0xee, 0xfb);

    /** Space left and right of the text in every cell. */
    static final int CELL_PADDING = 8;

    private static final int ROW_EXTRA_HEIGHT = 8;
    private static final int NAME_WIDTH = 240;
    private static final int DESCRIPTION_WIDTH = 360;
    private static final int AUTHOR_WIDTH = 150;
    private static final int STATUS_WIDTH = 110;
    private static final int TRASH_WIDTH = 40;
    private static final Dimension SIZE = new Dimension(900, 340);

    /** Wraps a long description in its tooltip. */
    private static final int TOOLTIP_WIDTH = 360;

    private static final int MARGIN = 8;
    private static final int LINE_GAP = 6;

    private static final String TABLE_CARD = "table";
    private static final String EMPTY_CARD = "empty";

    private final ServiceAppManagerModel model;
    private final Confirmer confirmer;
    private final RowsTableModel tableModel = new RowsTableModel();
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JTable table;
    private final JLabel emptyLabel = new JLabel(EMPTY_TEXT, SwingConstants.CENTER);
    private final JLabel appStoreLink = new JLabel();
    private String shownCard;

    /** The app whose input form is open after a double click, or null. Read on the EDT only. */
    private String invoking;

    /**
     * @param confirmer asks the user to confirm an uninstall
     * @param iconFont Desktop's icon font, which draws the trashcan
     * @param appStore the App Store page to point to when no service apps are installed
     * @param openUrl opens a web address in the system browser
     */
    public ServiceAppManagerDialog(
            Window owner,
            ServiceAppManagerModel model,
            Confirmer confirmer,
            Font iconFont,
            AppStoreLink appStore,
            Consumer<String> openUrl) {
        super(owner, TITLE, ModalityType.MODELESS);
        this.model = model;
        this.confirmer = confirmer;
        setName("serviceAppsManager");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        table =
                new JTable(tableModel) {
                    @Override
                    public Component prepareRenderer(
                            TableCellRenderer renderer, int row, int column) {
                        if (renderer instanceof JComponent plain) {
                            // Names, descriptions and authors come from the service's metadata.
                            PlainText.of(plain);
                        }
                        Component cell = super.prepareRenderer(renderer, row, column);
                        decorate(cell, row, column);
                        return cell;
                    }
                };
        table.setName("serviceAppsTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(table.getRowHeight() + ROW_EXTRA_HEIGHT);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(NAME).setPreferredWidth(NAME_WIDTH);
        table.getColumnModel().getColumn(DESCRIPTION).setPreferredWidth(DESCRIPTION_WIDTH);
        table.getColumnModel().getColumn(AUTHOR).setPreferredWidth(AUTHOR_WIDTH);
        table.getColumnModel().getColumn(STATUS).setPreferredWidth(STATUS_WIDTH);
        table.getColumnModel().getColumn(STATUS).setCellRenderer(new StatusRenderer());
        table.getColumnModel().getColumn(TRASH).setPreferredWidth(TRASH_WIDTH);
        table.getColumnModel().getColumn(TRASH).setMaxWidth(TRASH_WIDTH);
        table.getColumnModel().getColumn(TRASH).setCellRenderer(new TrashRenderer(iconFont));
        // Enter runs the selected app, as a double click does; it replaces the table's default of
        // moving to the next row, and keeps Enter from pressing Close.
        table.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "runSelected");
        table.getActionMap()
                .put(
                        "runSelected",
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                invokeAt(table.getSelectedRow());
                            }
                        });
        // Delete or Backspace asks to uninstall the selected app, as a click on its trashcan does.
        table.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "uninstallSelected");
        table.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "uninstallSelected");
        table.getActionMap()
                .put(
                        "uninstallSelected",
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                selected().ifPresent(ServiceAppManagerDialog.this::uninstall);
                            }
                        });
        table.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        int row = table.rowAtPoint(e.getPoint());
                        if (table.columnAtPoint(e.getPoint()) == TRASH) {
                            // The second click of a double click on the trashcan asks nothing more.
                            if (e.getClickCount() == 1 && row >= 0) {
                                uninstall(tableModel.rows.get(row));
                            }
                        } else if (e.getClickCount() == 2) {
                            invokeAt(row);
                        }
                    }
                });

        emptyLabel.setName("emptyLabel");
        JScrollPane scroll = new JScrollPane(table);
        content.add(scroll, TABLE_CARD);
        content.add(emptyState(appStore, openUrl), EMPTY_CARD);

        JButton close = new JButton("Close");
        close.setName("close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(close);

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(MARGIN, MARGIN, 0, MARGIN));
        root.add(content, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        // A click anywhere other than on a row clears the selection.
        MouseAdapter deselect =
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (e.getComponent() != table || table.rowAtPoint(e.getPoint()) < 0) {
                            table.clearSelection();
                        }
                    }
                };
        for (Component area :
                new Component[] {
                    root,
                    content,
                    buttons,
                    scroll,
                    scroll.getViewport(),
                    table.getTableHeader(),
                    table
                }) {
            area.addMouseListener(deselect);
        }
        getRootPane().setDefaultButton(close);
        getRootPane()
                .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getRootPane()
                .getActionMap()
                .put(
                        "close",
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                dispose();
                            }
                        });
        addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        model.close();
                    }
                });

        model.addChangeListener(() -> SwingUtilities.invokeLater(this::reload));
        setPreferredSize(SIZE);
        pack();
        setLocationRelativeTo(owner);
        model.open();
        reload();
    }

    /**
     * The centered "No Service Apps installed" message, then a line pointing to the App Store ("Go
     * to <App Store host> and choose a service app to install", where the host is a link to the App
     * Store's service apps page that opens the system browser), then how to install from the
     * Command Line when the service app's URL is known.
     */
    private JPanel emptyState(AppStoreLink appStore, Consumer<String> openUrl) {
        // Styled like Desktop's own links (the Starter Panel's): link-colored text, a hand
        // cursor, underlined while the pointer is over it.
        appStoreLink.setName("appStoreLink");
        appStoreLink.setForeground(DesktopColors.link());
        appStoreLink.setText(appStore.label());
        appStoreLink.setToolTipText(appStore.getServiceList());
        appStoreLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        appStoreLink.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        openUrl.accept(appStore.getServiceList());
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        appStoreLink.setText("<html><u>" + appStore.label() + "</u></html>");
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        appStoreLink.setText(appStore.label());
                    }
                });

        JPanel hint = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        hint.setName("appStoreHint");
        hint.setOpaque(false);
        hint.add(new JLabel("Go to "));
        hint.add(appStoreLink);
        hint.add(new JLabel(" and choose a service app to install"));

        JLabel commandHint = new JLabel(COMMAND_HINT, SwingConstants.CENTER);
        commandHint.setName("commandHint");

        // Read-only but selectable, so the command can be copied into the Command Line.
        JTextField command = new JTextField(INSTALL_COMMAND);
        command.setName("installCommand");
        command.setEditable(false);
        command.setBorder(BorderFactory.createEmptyBorder());
        command.setOpaque(false);
        command.setHorizontalAlignment(SwingConstants.CENTER);
        command.setFont(new Font(Font.MONOSPACED, Font.PLAIN, emptyLabel.getFont().getSize()));

        JPanel lines = new JPanel(new GridLayout(4, 1, 0, LINE_GAP));
        lines.setOpaque(false);
        lines.add(emptyLabel);
        lines.add(hint);
        lines.add(commandHint);
        lines.add(command);

        JPanel centered = new JPanel(new GridBagLayout());
        centered.add(lines);
        return centered;
    }

    /** Re-reads the rows, keeping the selected app selected. Runs on the EDT. */
    void reload() {
        String selectedName = selected().map(ServiceAppManagerModel.Row::name).orElse(null);
        tableModel.setRows(model.rows());
        for (int row = 0; row < tableModel.rows.size(); row++) {
            if (tableModel.rows.get(row).name().equals(selectedName)) {
                table.setRowSelectionInterval(row, row);
            }
        }
        shownCard = tableModel.rows.isEmpty() ? EMPTY_CARD : TABLE_CARD;
        cards.show(content, shownCard);
    }

    private Optional<ServiceAppManagerModel.Row> selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= tableModel.rows.size()
                ? Optional.empty()
                : Optional.of(tableModel.rows.get(row));
    }

    /** Why the app can't be invoked now, or null when it can. */
    static String invokeBlocker(ServiceAppManagerModel.Row row) {
        if (row.status() == ServiceAppManagerModel.Status.CHECKING) {
            return "checking whether it is online";
        }
        if (row.status() == ServiceAppManagerModel.Status.OFFLINE) {
            return "it is offline";
        }
        return row.invokable() ? null : row.invokeDisabledReason();
    }

    /**
     * A double click: invokes the app if it is Online and runnable. The row is light blue while its
     * input form is open; Desktop's task manager returns once the form closes.
     *
     * @return whether the app was invoked
     */
    private boolean invokeAt(int row) {
        if (row < 0 || row >= tableModel.rows.size()) {
            return false;
        }
        ServiceAppManagerModel.Row app = tableModel.rows.get(row);
        if (invokeBlocker(app) != null) {
            return false;
        }
        table.clearSelection();
        invoking = app.name();
        table.repaint();
        // After this click finishes painting, open the modal input form.
        SwingUtilities.invokeLater(
                () -> {
                    try {
                        model.invoke(app.name());
                    } finally {
                        invoking = null;
                        table.repaint();
                    }
                });
        return true;
    }

    public JTable table() {
        return table;
    }

    public JLabel emptyLabel() {
        return emptyLabel;
    }

    /** The App Store link shown when no service apps are installed. */
    public JLabel appStoreLink() {
        return appStoreLink;
    }

    /** Whether the table is the card showing, rather than the empty message. */
    public boolean showsTable() {
        return TABLE_CARD.equals(shownCard);
    }

    /** The app whose input form is open after a double click, or null. */
    String invoking() {
        return invoking;
    }

    /**
     * Padding, the row's highlight, and the cell's tooltip: what a double click does on the name,
     * the full text of a description or author cut off in its cell, the status check's finding. Set
     * on every call: a cell renderer keeps the last background and border it was given.
     */
    private void decorate(Component cell, int row, int column) {
        if (!(cell instanceof JComponent component)) {
            return;
        }
        ServiceAppManagerModel.Row data = tableModel.rows.get(row);
        component.setBorder(BorderFactory.createEmptyBorder(0, CELL_PADDING, 0, CELL_PADDING));
        component.setBackground(
                data.name().equals(invoking)
                        ? INVOKING_BACKGROUND
                        : table.isRowSelected(row) ? SELECTED_BACKGROUND : table.getBackground());
        if (column == NAME) {
            component.setForeground(table.getForeground());
            String blocker = invokeBlocker(data);
            component.setToolTipText(
                    PlainText.tooltip(
                            data.name()
                                    + " "
                                    + data.version()
                                    + " at "
                                    + data.url()
                                    + (blocker == null
                                            ? ". Double-click to run it."
                                            : ". Can't run: " + blocker + ".")));
        } else if (column == DESCRIPTION) {
            component.setToolTipText(PlainText.tooltip(data.description(), TOOLTIP_WIDTH));
        } else if (column == AUTHOR) {
            component.setToolTipText(PlainText.tooltip(data.author()));
        } else if (column == TRASH) {
            component.setToolTipText(PlainText.tooltip("Uninstall " + data.name()));
        } else {
            component.setToolTipText(PlainText.tooltip(data.statusDetail()));
        }
    }

    /** Status in bold green for Online and bold red for Offline; Checking… is plain. */
    private final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            Color color =
                    switch (tableModel.rows.get(row).status()) {
                        case ONLINE -> DesktopColors.success();
                        case OFFLINE -> DesktopColors.error();
                        default -> null;
                    };
            // Set on every call: this renderer keeps the last color and font it was given.
            setForeground(color != null ? color : table.getForeground());
            setFont(color != null ? table.getFont().deriveFont(Font.BOLD) : table.getFont());
            return this;
        }
    }

    /** The trashcan, centered, in Desktop's icon font. */
    private static final class TrashRenderer extends DefaultTableCellRenderer {
        private final Font iconFont;

        TrashRenderer(Font iconFont) {
            this.iconFont = iconFont;
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column) {
            super.getTableCellRendererComponent(
                    table, IconManager.ICON_TRASH_O, isSelected, hasFocus, row, column);
            // The superclass sets the table's font on every call, so the icon font goes last.
            setFont(iconFont);
            setForeground(table.getForeground());
            return this;
        }
    }

    /** The question asked before an app is uninstalled. */
    static String uninstallQuestion(String name) {
        return "Are you sure you want to uninstall " + name + "?";
    }

    private void uninstall(ServiceAppManagerModel.Row row) {
        if (confirmer.confirm(this, "Uninstall Service App", uninstallQuestion(row.name()))) {
            // The registry event reloads the table.
            model.uninstall(row.name());
        }
    }

    private final class RowsTableModel extends AbstractTableModel {

        private final String[] columns = {"Name", "Description", "Author", "Status", ""};
        private List<ServiceAppManagerModel.Row> rows = List.of();

        void setRows(List<ServiceAppManagerModel.Row> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            ServiceAppManagerModel.Row data = rows.get(row);
            return switch (column) {
                case NAME -> data.name();
                case DESCRIPTION -> data.description();
                case AUTHOR -> data.author();
                case STATUS -> data.status().text();
                default -> IconManager.ICON_TRASH_O;
            };
        }
    }
}
