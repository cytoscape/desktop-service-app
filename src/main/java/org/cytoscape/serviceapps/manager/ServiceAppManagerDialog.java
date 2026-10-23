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
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

/**
 * The Service Apps dialog: every installed app with its status, an Invoke checkbox and an Uninstall
 * checkbox, or a centered "No Service Apps installed" label. Non-modal; renders {@link
 * ServiceAppManagerModel} and closes with Close, the window's X or Escape.
 */
public class ServiceAppManagerDialog extends JDialog {

    public static final String TITLE = "Service Apps";
    public static final String EMPTY_TEXT = "No Service Apps installed";

    /** Points users who already know a service app's URL to the install command. */
    public static final String COMMAND_HINT =
            "If you know a service app's URL, install it from the Command Line"
                    + " (View > Show Command Panel):";

    public static final String INSTALL_COMMAND = "serviceapps install url=<service URL>";

    /** Desktop's theme link color; a standard link blue outside Cytoscape, such as in tests. */
    static final Color FALLBACK_LINK_COLOR = new Color(0x1a, 0x5f, 0xb4);

    static final int NAME = 0;
    static final int STATUS = 1;
    static final int INVOKE = 2;
    static final int UNINSTALL = 3;

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

    /**
     * @param appStore the App Store page to point to when no service apps are installed
     * @param openUrl opens a web address in the system browser
     */
    public ServiceAppManagerDialog(
            Window owner,
            ServiceAppManagerModel model,
            Confirmer confirmer,
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
                        Component cell = super.prepareRenderer(renderer, row, column);
                        decorate(cell, row, column);
                        return cell;
                    }
                };
        table.setName("serviceAppsTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(NAME).setPreferredWidth(240);
        table.getColumnModel().getColumn(STATUS).setPreferredWidth(90);
        table.getColumnModel().getColumn(INVOKE).setPreferredWidth(60);
        table.getColumnModel().getColumn(UNINSTALL).setPreferredWidth(70);

        emptyLabel.setName("emptyLabel");
        content.add(new JScrollPane(table), TABLE_CARD);
        content.add(emptyState(appStore, openUrl), EMPTY_CARD);

        JButton close = new JButton("Close");
        close.setName("close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(close);

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        root.add(content, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
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
        setPreferredSize(new Dimension(720, 340));
        pack();
        setLocationRelativeTo(owner);
        model.open();
        reload();
    }

    /**
     * The centered "No Service Apps installed" message, then a line pointing to the App Store ("Go
     * to apps.cytoscape.org and choose a service app to install", where the host is a link to the
     * App Store's service apps page that opens the system browser), then how to install from the
     * Command Line when the service app's URL is known.
     */
    private JPanel emptyState(AppStoreLink appStore, Consumer<String> openUrl) {
        // Styled like Desktop's own links (the Starter Panel's): link-colored text, a hand
        // cursor, underlined while the pointer is over it.
        appStoreLink.setName("appStoreLink");
        appStoreLink.setForeground(linkColor());
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

        JPanel lines = new JPanel(new GridLayout(4, 1, 0, 6));
        lines.setOpaque(false);
        lines.add(emptyLabel);
        lines.add(hint);
        lines.add(commandHint);
        lines.add(command);

        JPanel centered = new JPanel(new GridBagLayout());
        centered.add(lines);
        return centered;
    }

    static Color linkColor() {
        Color color = UIManager.getColor("CyColor.complement(+1)");
        return color != null ? color : FALLBACK_LINK_COLOR;
    }

    /** Re-reads the rows. Runs on the EDT. */
    void reload() {
        tableModel.setRows(model.rows());
        cards.show(content, tableModel.rows.isEmpty() ? EMPTY_CARD : TABLE_CARD);
    }

    public JTable table() {
        return table;
    }

    public JLabel emptyLabel() {
        return emptyLabel;
    }

    /** The {@code apps.cytoscape.org} link shown when no service apps are installed. */
    public JLabel appStoreLink() {
        return appStoreLink;
    }

    /** Whether the table is the card showing, rather than the empty label. */
    public boolean showsTable() {
        return table.getParent().getParent().isVisible();
    }

    private void decorate(Component cell, int row, int column) {
        if (!(cell instanceof JComponent component)) {
            return;
        }
        ServiceAppManagerModel.Row data = tableModel.rows.get(row);
        String tooltip = null;
        switch (column) {
            case NAME -> tooltip = data.name() + " " + data.version() + " at " + data.url();
            case STATUS -> tooltip = data.statusDetail();
            case INVOKE -> {
                component.setEnabled(data.invokable());
                tooltip =
                        data.invokable()
                                ? "Run " + data.name()
                                : "Can't run: " + data.invokeDisabledReason();
            }
            case UNINSTALL -> tooltip = "Uninstall " + data.name();
            default -> {}
        }
        component.setToolTipText(tooltip);
    }

    private void tickInvoke(ServiceAppManagerModel.Row row) {
        // Untick first (the value is never stored), then open the modal input dialog.
        SwingUtilities.invokeLater(() -> model.invoke(row.name()));
    }

    private void tickUninstall(ServiceAppManagerModel.Row row) {
        String question = "Uninstall " + row.name() + "? Are you sure?";
        if (confirmer.confirm(this, "Uninstall Service App", question)) {
            // The registry event reloads the table.
            model.uninstall(row.name());
        }
    }

    private final class RowsTableModel extends AbstractTableModel {

        private final String[] columns = {"Name", "Status", "Invoke", "Uninstall"};
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
        public Class<?> getColumnClass(int column) {
            return column == INVOKE || column == UNINSTALL ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == UNINSTALL || (column == INVOKE && rows.get(row).invokable());
        }

        @Override
        public Object getValueAt(int row, int column) {
            ServiceAppManagerModel.Row data = rows.get(row);
            return switch (column) {
                case NAME -> data.name();
                case STATUS -> data.status().text();
                default -> Boolean.FALSE;
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (!Boolean.TRUE.equals(value) || row >= rows.size()) {
                return;
            }
            ServiceAppManagerModel.Row data = rows.get(row);
            if (column == INVOKE) {
                tickInvoke(data);
            } else if (column == UNINSTALL) {
                tickUninstall(data);
            }
        }
    }
}
