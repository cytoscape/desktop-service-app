package org.cytoscape.serviceapps.manager;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.FixtureServerExtension;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.testsupport.TestNetworks;
import org.cytoscape.serviceapps.ui.DesktopColors;

import static org.cytoscape.serviceapps.testsupport.Waits.until;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** The Swing dialog over a real model. Needs a display; CI runs it under xvfb. */
@ExtendWith(FixtureServerExtension.class)
class ServiceAppManagerDialogTest {

    @TempDir Path dir;

    private ManagerFixture f;
    private final List<String> questions = new ArrayList<>();
    private final List<String> opened = new ArrayList<>();
    private boolean confirm;
    private ServiceAppManagerDialog dialog;

    @BeforeEach
    void setUp(FixtureServer fixture) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display; CI runs it under xvfb");
        f = new ManagerFixture(fixture, dir);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dialog != null) {
            onEdt(dialog::dispose);
        }
    }

    private void open() throws Exception {
        open(AppStoreLink.from(new Properties()));
    }

    private void open(AppStoreLink appStore) throws Exception {
        onEdt(
                () ->
                        dialog =
                                new ServiceAppManagerDialog(
                                        null,
                                        f.model(),
                                        (parent, title, question) -> {
                                            questions.add(question);
                                            return confirm;
                                        },
                                        appStore,
                                        opened::add));
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    /** Lets queued EDT work, such as reloads after registry events, finish. */
    private static void flush() throws Exception {
        onEdt(() -> {});
        onEdt(() -> {});
    }

    private JTable table() {
        return dialog.table();
    }

    private int rowOf(String name) {
        for (int row = 0; row < table().getRowCount(); row++) {
            if (name.equals(table().getValueAt(row, ServiceAppManagerDialog.NAME))) {
                return row;
            }
        }
        return -1;
    }

    /** The cell's tooltip as the user reads it: without its HTML wrapper and escapes. */
    private String tooltip(int row, int column) throws Exception {
        String[] text = new String[1];
        onEdt(
                () -> {
                    Component cell =
                            table().prepareRenderer(
                                            table().getCellRenderer(row, column), row, column);
                    text[0] = ((JComponent) cell).getToolTipText();
                });
        assertTrue(text[0].startsWith("<html>") && text[0].endsWith("</html>"), text[0]);
        return text[0].substring(6, text[0].length() - 7)
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    private void select(String name) throws Exception {
        int row = rowOf(name);
        onEdt(() -> table().setRowSelectionInterval(row, row));
    }

    /** {@code name}'s cell in {@code column}, as the table renders it. */
    private JLabel cell(String name, int column) throws Exception {
        int row = rowOf(name);
        Component[] cell = new Component[1];
        onEdt(
                () ->
                        cell[0] =
                                table().prepareRenderer(
                                                table().getCellRenderer(row, column), row, column));
        return (JLabel) cell[0];
    }

    private JLabel statusCell(String name) throws Exception {
        return cell(name, ServiceAppManagerDialog.STATUS);
    }

    private JLabel nameCell(String name) throws Exception {
        // Render a status cell first, so a color leaking into the shared renderer would show.
        statusCell(name);
        return cell(name, ServiceAppManagerDialog.NAME);
    }

    /** A mouse press at {@code (x, y)} on {@code target}, as a click there starts. */
    private static void press(Component target, int x, int y) throws Exception {
        onEdt(() -> mouse(target, MouseEvent.MOUSE_PRESSED, x, y, 1));
    }

    /**
     * A double click at the middle of {@code name}'s row, as Swing delivers it: a press, a second
     * press, then the click. Fails if the row shows the red highlight at any point on the way.
     * Returns what the dialog shows right after, before any queued work such as the launch runs.
     */
    private String doubleClick(String name) throws Exception {
        int row = rowOf(name);
        Rectangle cell = table().getCellRect(row, ServiceAppManagerDialog.NAME, true);
        int x = cell.x + 2;
        int y = cell.y + cell.height / 2;
        List<String> states = new ArrayList<>();
        onEdt(
                () -> {
                    mouse(table(), MouseEvent.MOUSE_PRESSED, x, y, 1);
                    states.add(rowState(row));
                    mouse(table(), MouseEvent.MOUSE_PRESSED, x, y, 2);
                    states.add(rowState(row));
                    mouse(table(), MouseEvent.MOUSE_CLICKED, x, y, 2);
                    states.add(rowState(row));
                });
        for (String state : states.subList(0, 2)) {
            assertFalse(state.startsWith("red"), "no red flash before the second click: " + states);
        }
        return states.get(2);
    }

    /** The row's color name, the selected row and the Uninstall state. Runs on the EDT. */
    private String rowState(int row) {
        Color color =
                table().prepareRenderer(
                                table().getCellRenderer(row, ServiceAppManagerDialog.NAME),
                                row,
                                ServiceAppManagerDialog.NAME)
                        .getBackground();
        String name =
                color.equals(ServiceAppManagerDialog.INVOKING_BACKGROUND)
                        ? "blue"
                        : color.equals(ServiceAppManagerDialog.SELECTED_BACKGROUND)
                                ? "red"
                                : color.equals(table().getBackground())
                                        ? "plain"
                                        : color.toString();
        return name
                + ", selected row "
                + table().getSelectedRow()
                + ", uninstall "
                + dialog.uninstallButton().isEnabled();
    }

    /** The background of {@code name}'s cells, both columns checked to agree. */
    private Color background(String name) throws Exception {
        Color status = statusCell(name).getBackground();
        assertEquals(status, nameCell(name).getBackground(), "both cells share the row's color");
        return status;
    }

    private String state(String name) throws Exception {
        String[] state = new String[1];
        onEdt(() -> state[0] = rowState(rowOf(name)));
        return state[0];
    }

    /** Records each launch with the row's state at the moment the input form would open. */
    private List<String> recordLaunches(String name) {
        List<String> launches = new ArrayList<>();
        doAnswer(
                        call -> {
                            launches.add(call.getArgument(0) + ": " + rowState(rowOf(name)));
                            return call.callRealMethod();
                        })
                .when(f.launcher)
                .launch(anyString());
        return launches;
    }

    @Test
    void withNoAppsTheDialogShowsTheCenteredLabelInsteadOfATable() throws Exception {
        open();

        assertEquals("Service Apps", dialog.getTitle());
        assertFalse(dialog.isModal());
        assertEquals("No Service Apps installed", dialog.emptyLabel().getText());
        assertEquals(SwingConstants.CENTER, dialog.emptyLabel().getHorizontalAlignment());
        assertFalse(dialog.showsTable());
    }

    /** The "Go to <host> and choose a service app to install" line, as displayed. */
    private String appStoreHint() {
        Container hint = (Container) find(dialog.getContentPane(), "appStoreHint");
        StringBuilder text = new StringBuilder();
        for (Component part : hint.getComponents()) {
            text.append(((JLabel) part).getText());
        }
        return text.toString();
    }

    /** The link shows {@code label}, underlines on hover, and opens {@code url} on click. */
    private void assertLink(String label, String url) throws Exception {
        assertEquals("Go to " + label + " and choose a service app to install", appStoreHint());
        JLabel link = dialog.appStoreLink();
        assertEquals(label, link.getText());
        assertEquals(url, link.getToolTipText());
        // Styled like Desktop's own links: link color, hand cursor, underline on hover only.
        assertEquals(DesktopColors.link(), link.getForeground());
        assertEquals(Cursor.HAND_CURSOR, link.getCursor().getType());

        onEdt(() -> mouse(link, MouseEvent.MOUSE_ENTERED, 2, 2, 1));
        assertEquals("<html><u>" + label + "</u></html>", link.getText());
        onEdt(() -> mouse(link, MouseEvent.MOUSE_EXITED, 2, 2, 1));
        assertEquals(label, link.getText());
        assertTrue(opened.isEmpty(), "hovering opens nothing");

        onEdt(() -> mouse(link, MouseEvent.MOUSE_CLICKED, 2, 2, 1));

        assertEquals(List.of(url), opened);
    }

    @Test
    void withNoAppStoreSettingTheLinkGoesToThePublicAppStoresServiceApps() throws Exception {
        open(AppStoreLink.from(new Properties()));

        assertLink("apps.cytoscape.org", "https://apps.cytoscape.org/apps/platform/service/");
    }

    @Test
    void withAnAppStoreSettingTheLinkGoesToThatSitesServiceApps() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("appStoreDownloadSiteUrl1", "https://test.apps.example.org/");

        open(AppStoreLink.from(properties));

        assertLink("test.apps.example.org", "https://test.apps.example.org/apps/platform/service/");
    }

    @Test
    void withNoAppsTheDialogAlsoShowsTheInstallCommand() throws Exception {
        open();

        assertEquals(
                "If you know a service app's URL, install it from the Command Line (View > Show"
                        + " Command Panel):",
                ((JLabel) find(dialog.getContentPane(), "commandHint")).getText());
        JTextField command = (JTextField) find(dialog.getContentPane(), "installCommand");
        assertEquals("serviceapps install url=<service URL>", command.getText());
        assertFalse(command.isEditable(), "selectable for copying, not editable");
    }

    @Test
    void appsAreListedWithTheirNameAndStatusInBoldGreenOrRed() throws Exception {
        String hello = f.install("hello");
        String noMenu = f.install("menu-root-missing");
        String offline = f.installOffline("Offline app");
        open();

        assertTrue(dialog.showsTable());
        assertEquals(
                List.of("Name", "Description", "Author", "Status"),
                IntStream.range(0, table().getColumnCount())
                        .mapToObj(table()::getColumnName)
                        .toList());
        assertEquals(3, table().getRowCount());
        assertEquals("Checking…", table().getValueAt(rowOf(hello), ServiceAppManagerDialog.STATUS));
        assertFalse(statusCell(hello).getFont().isBold(), "Checking… is plain");

        f.checks.runAll();
        flush();

        assertEquals("Online", table().getValueAt(rowOf(hello), ServiceAppManagerDialog.STATUS));
        assertEquals("Online", table().getValueAt(rowOf(noMenu), ServiceAppManagerDialog.STATUS));
        assertEquals("Offline", table().getValueAt(rowOf(offline), ServiceAppManagerDialog.STATUS));
        assertEquals(DesktopColors.success(), statusCell(hello).getForeground());
        assertTrue(statusCell(hello).getFont().isBold());
        assertEquals(DesktopColors.error(), statusCell(offline).getForeground());
        assertTrue(statusCell(offline).getFont().isBold());
        JLabel name = nameCell(hello);
        assertEquals(table().getForeground(), name.getForeground(), "names keep the plain color");
        assertFalse(name.getFont().isBold());
        for (int column = 0; column < table().getColumnCount(); column++) {
            assertFalse(table().isCellEditable(rowOf(hello), column));
        }
    }

    @Test
    void metadataIsShownAsTextNeverAsHtml() throws Exception {
        String payload = "<html><img src='http://127.0.0.1:9/x.png'></html>";
        String escaped = "&lt;html&gt;&lt;img src='http://127.0.0.1:9/x.png'&gt;&lt;/html&gt;";
        ServiceMetadata meta = TestNetworks.scenario("hello");
        meta.name = payload;
        meta.description = payload;
        meta.author = payload;
        f.registry.install(payload, meta.version, ManagerFixture.OFFLINE_URL, meta);
        open();

        for (int column :
                new int[] {
                    ServiceAppManagerDialog.NAME,
                    ServiceAppManagerDialog.DESCRIPTION,
                    ServiceAppManagerDialog.AUTHOR
                }) {
            JLabel cell = cell(payload, column);
            assertEquals(payload, cell.getText());
            assertNull(cell.getClientProperty(BasicHTML.propertyKey), "column " + column);
            assertTrue(cell.getToolTipText().contains(escaped), cell.getToolTipText());
            assertFalse(cell.getToolTipText().contains("<img"), cell.getToolTipText());
        }
        assertEquals(
                "<html>" + escaped + "</html>",
                cell(payload, ServiceAppManagerDialog.AUTHOR).getToolTipText());
    }

    @Test
    void descriptionAndAuthorAreListedWithTheFullDescriptionOnHover() throws Exception {
        String hello = f.install("hello");
        ServiceMetadata sparse = TestNetworks.scenario("hello");
        sparse.name = "Sparse";
        sparse.description = "  Finds   communities\n in <large> networks & more.  ";
        sparse.author = null;
        f.registry.install(sparse.name, sparse.version, ManagerFixture.OFFLINE_URL, sparse);
        ServiceMetadata bare = TestNetworks.scenario("hello");
        bare.name = "Bare";
        bare.description = null;
        bare.author = " ";
        f.registry.install(bare.name, bare.version, ManagerFixture.OFFLINE_URL, bare);
        open();

        int row = rowOf(hello);
        assertEquals(
                "A fixture service app for tests",
                table().getValueAt(row, ServiceAppManagerDialog.DESCRIPTION));
        assertEquals("Cytoscape team", table().getValueAt(row, ServiceAppManagerDialog.AUTHOR));
        assertEquals(
                "<html>Cytoscape team</html>",
                cell(hello, ServiceAppManagerDialog.AUTHOR).getToolTipText(),
                "an author cut off in its cell can be read on hover");

        assertEquals(
                "Finds communities in <large> networks & more.",
                table().getValueAt(rowOf("Sparse"), ServiceAppManagerDialog.DESCRIPTION),
                "on one line");
        String tooltip = cell("Sparse", ServiceAppManagerDialog.DESCRIPTION).getToolTipText();
        assertTrue(tooltip.startsWith("<html><div style='width:"), tooltip);
        assertTrue(
                tooltip.contains("Finds communities in &lt;large&gt; networks &amp; more."),
                tooltip);
        assertEquals("", table().getValueAt(rowOf("Sparse"), ServiceAppManagerDialog.AUTHOR));
        assertNull(cell("Sparse", ServiceAppManagerDialog.AUTHOR).getToolTipText());

        assertEquals("", table().getValueAt(rowOf("Bare"), ServiceAppManagerDialog.DESCRIPTION));
        assertNull(cell("Bare", ServiceAppManagerDialog.DESCRIPTION).getToolTipText());
        assertEquals("", table().getValueAt(rowOf("Bare"), ServiceAppManagerDialog.AUTHOR));
        assertEquals(
                ServiceAppManagerDialog.CELL_PADDING,
                cell("Bare", ServiceAppManagerDialog.DESCRIPTION).getInsets().left);
    }

    @Test
    void cellsArePaddedAndUnhighlightedRowsKeepTheTableBackground() throws Exception {
        String hello = f.install("hello");
        open();

        Insets padding = nameCell(hello).getInsets();
        assertEquals(ServiceAppManagerDialog.CELL_PADDING, padding.left);
        assertEquals(ServiceAppManagerDialog.CELL_PADDING, padding.right);
        assertEquals(ServiceAppManagerDialog.CELL_PADDING, statusCell(hello).getInsets().left);
        assertEquals(table().getBackground(), background(hello));
    }

    @Test
    void onlyUninstallSitsLeftOfCloseAndStartsDisabled() throws Exception {
        f.install("hello");
        open();

        Container buttons = dialog.uninstallButton().getParent();
        assertEquals(
                List.of("Uninstall", "Close"),
                Arrays.stream(buttons.getComponents()).map(c -> ((JButton) c).getText()).toList());
        assertFalse(dialog.uninstallButton().isEnabled());
        assertEquals("Select a service app first", dialog.uninstallButton().getToolTipText());
    }

    @Test
    void aSingleClickHighlightsTheRowLightRedAndEnablesUninstall() throws Exception {
        String hello = f.install("hello");
        String offline = f.installOffline("Offline app");
        open();
        f.checks.runAll();
        flush();

        for (String name : List.of(hello, offline)) {
            Rectangle row = table().getCellRect(rowOf(name), ServiceAppManagerDialog.NAME, true);
            press(table(), row.x + 2, row.y + row.height / 2);
            assertEquals(
                    "plain, selected row " + rowOf(name) + ", uninstall false",
                    state(name),
                    "held back while a second click may come");

            until(() -> dialog.uninstallButton().isEnabled());

            assertEquals(rowOf(name), table().getSelectedRow());
            assertEquals(ServiceAppManagerDialog.SELECTED_BACKGROUND, background(name));
            assertTrue(dialog.uninstallButton().isEnabled());
            assertEquals("Uninstall " + name, dialog.uninstallButton().getToolTipText());
            // Status keeps its color on the light highlight.
            assertEquals(
                    name.equals(hello) ? DesktopColors.success() : DesktopColors.error(),
                    statusCell(name).getForeground());
            assertTrue(statusCell(name).getFont().isBold(), "still bold when highlighted");
            assertEquals(table().getForeground(), nameCell(name).getForeground());
        }
        assertEquals(table().getBackground(), background(hello), "the other row is cleared");
    }

    @Test
    void doubleClickingAnOnlineAppInvokesItWithTheRowLightBlueUntilTheFormCloses()
            throws Exception {
        String hello = f.install("hello");
        open();
        f.checks.runAll();
        flush();
        select(hello);
        List<String> launches = recordLaunches(hello);

        assertEquals("red, selected row 0, uninstall true", state(hello));

        assertEquals("blue, selected row -1, uninstall false", doubleClick(hello));
        flush();

        assertEquals(
                List.of(hello + ": blue, selected row -1, uninstall false"),
                launches,
                "launched once, with the row light blue while its form is open");
        verify(f.launcher, times(1)).launch(hello);
        assertNull(dialog.invoking(), "the form has closed");
        assertEquals("plain, selected row -1, uninstall false", state(hello));
        assertTrue(dialog.isDisplayable());
    }

    @Test
    void theHighlightClearsEvenIfTheLaunchFails() throws Exception {
        String hello = f.install("hello");
        open();
        f.checks.runAll();
        flush();
        doThrow(new IllegalStateException("boom")).when(f.launcher).launch(hello);
        List<Throwable> thrown = new ArrayList<>();
        Thread.UncaughtExceptionHandler[] previous = new Thread.UncaughtExceptionHandler[1];
        onEdt(
                () -> {
                    previous[0] = Thread.currentThread().getUncaughtExceptionHandler();
                    Thread.currentThread().setUncaughtExceptionHandler((t, e) -> thrown.add(e));
                });
        try {
            assertEquals("blue, selected row -1, uninstall false", doubleClick(hello));
            flush();
        } finally {
            onEdt(() -> Thread.currentThread().setUncaughtExceptionHandler(previous[0]));
        }

        assertEquals(1, thrown.size(), "the failure still reaches the EDT's handler");
        assertEquals("boom", thrown.get(0).getMessage());
        assertNull(dialog.invoking());
        assertEquals("plain, selected row -1, uninstall false", state(hello));
    }

    /**
     * Enter on the table, through whatever the table binds Enter to when it has the focus. Returns
     * what the dialog shows right after, before queued work such as the launch runs.
     */
    private String pressEnter(String name) throws Exception {
        Object key =
                table().getInputMap(JComponent.WHEN_FOCUSED)
                        .get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
        String[] after = new String[1];
        onEdt(
                () -> {
                    table().getActionMap()
                            .get(key)
                            .actionPerformed(
                                    new ActionEvent(table(), ActionEvent.ACTION_PERFORMED, ""));
                    after[0] = name == null ? null : rowState(rowOf(name));
                });
        return after[0];
    }

    @Test
    void enterRunsTheSelectedOnlineAppWithTheRowLightBlueUntilTheFormCloses() throws Exception {
        String hello = f.install("hello");
        open();
        f.checks.runAll();
        flush();
        select(hello);
        List<String> launches = recordLaunches(hello);

        assertEquals("blue, selected row -1, uninstall false", pressEnter(hello));
        flush();

        assertEquals(List.of(hello + ": blue, selected row -1, uninstall false"), launches);
        verify(f.launcher, times(1)).launch(hello);
        assertEquals("plain, selected row -1, uninstall false", state(hello));
    }

    @Test
    void enterOnAnAppThatCantRunOrWithNothingSelectedDoesNothing() throws Exception {
        String checking = f.install("hello");
        String offline = f.installOffline("Offline app");
        open();
        assertEquals(
                "Checking…", table().getValueAt(rowOf(checking), ServiceAppManagerDialog.STATUS));

        assertEquals(null, pressEnter(null), "nothing selected");
        for (String name : List.of(checking, offline)) {
            select(name);
            assertEquals(
                    "red, selected row " + rowOf(name) + ", uninstall true",
                    pressEnter(name),
                    "still selected, nothing run: " + name);
        }
        flush();

        verify(f.launcher, never()).launch(anyString());
    }

    @Test
    void doubleClickingAnAppThatCantRunDoesNothingAndItsTooltipSaysWhy() throws Exception {
        String checking = f.install("hello");
        open();
        assertEquals(
                "Checking…", table().getValueAt(rowOf(checking), ServiceAppManagerDialog.STATUS));
        assertTrue(
                tooltip(rowOf(checking), ServiceAppManagerDialog.NAME)
                        .endsWith(". Can't run: checking whether it is online."));
        doubleClick(checking);
        flush();
        verify(f.launcher, never()).launch(anyString());
        onEdt(dialog::dispose);
        flush();

        String offline = f.installOffline("Offline app");
        String unsupported = f.install("unsupported-file");
        open();
        f.checks.runAll();
        flush();
        assertEquals(
                "Online", table().getValueAt(rowOf(unsupported), ServiceAppManagerDialog.STATUS));
        assertTrue(
                tooltip(rowOf(offline), ServiceAppManagerDialog.NAME)
                        .endsWith(". Can't run: it is offline."));
        assertTrue(
                tooltip(rowOf(unsupported), ServiceAppManagerDialog.NAME)
                        .contains(". Can't run: it uses the parameter type 'file'"),
                tooltip(rowOf(unsupported), ServiceAppManagerDialog.NAME));

        for (String name : List.of(offline, unsupported)) {
            assertEquals(
                    "red, selected row " + rowOf(name) + ", uninstall true",
                    doubleClick(name),
                    "nothing runs; the clicks select the row");
        }
        flush();
        verify(f.launcher, never()).launch(anyString());
    }

    @Test
    void anOnlineAppsTooltipOffersADoubleClickOnceItIsFoundOnline() throws Exception {
        String hello = f.install("hello");
        open();
        select(hello);

        f.checks.runAll();
        flush();

        assertEquals(rowOf(hello), table().getSelectedRow(), "the selection survives the refresh");
        assertEquals(
                hello
                        + " "
                        + TestNetworks.scenario("hello").version
                        + " at "
                        + f.fixture.url("hello")
                        + ". Double-click to run it.",
                tooltip(rowOf(hello), ServiceAppManagerDialog.NAME));
        assertTrue(
                tooltip(rowOf(hello), ServiceAppManagerDialog.STATUS)
                        .startsWith("Service status: "),
                "the status tooltip says what the check found");
    }

    @Test
    void cancellingUninstallChangesNothing() throws Exception {
        String hello = f.install("hello");
        open();
        confirm = false;
        select(hello);

        onEdt(dialog.uninstallButton()::doClick);
        flush();

        assertEquals(List.of("Uninstall " + hello + "? Are you sure?"), questions);
        verify(f.registry, never()).uninstall(anyString());
        assertEquals(1, table().getRowCount());
        assertEquals(0, table().getSelectedRow(), "still selected");
    }

    @Test
    void confirmingUninstallRemovesTheRowAndTheLastOneShowsTheLabel() throws Exception {
        String hello = f.install("hello");
        String other = f.install("menu-root-apps");
        open();
        confirm = true;
        select(hello);

        onEdt(dialog.uninstallButton()::doClick);
        flush();

        verify(f.registry).uninstall(hello);
        assertEquals(1, table().getRowCount());
        assertEquals(-1, table().getSelectedRow());
        assertFalse(dialog.uninstallButton().isEnabled());
        assertTrue(dialog.isDisplayable());

        select(other);
        onEdt(dialog.uninstallButton()::doClick);
        flush();

        assertFalse(dialog.showsTable());
        assertTrue(dialog.isDisplayable());
    }

    @Test
    void clickingAnywhereButTheHighlightedRowClearsItAndUninstall() throws Exception {
        String hello = f.install("hello");
        String other = f.install("menu-root-apps");
        open();
        f.checks.runAll();
        flush();
        onEdt(dialog::validate);
        JTable table = table();
        JScrollPane scroll =
                (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
        Component[] elsewhere = {
            table, // below the last row
            table.getTableHeader(),
            scroll,
            scroll.getViewport(),
            scroll.getParent(), // the content panel
            dialog.getContentPane(),
            dialog.uninstallButton().getParent()
        };

        for (Component area : elsewhere) {
            select(hello);
            assertTrue(dialog.uninstallButton().isEnabled());
            int y = area == table ? table.getHeight() - 2 : 2;

            press(area, 2, y);

            assertEquals(-1, table.getSelectedRow(), "cleared by a click on " + area);
            assertFalse(dialog.uninstallButton().isEnabled());
            assertEquals(table.getBackground(), background(hello));
        }

        select(hello);
        Rectangle row = table.getCellRect(rowOf(other), 0, true);
        press(table, row.x + 2, row.y + row.height / 2);
        assertEquals(rowOf(other), table.getSelectedRow(), "a click on another row moves it");
        assertEquals(table.getBackground(), background(hello));

        press(dialog.uninstallButton(), 2, 2);
        assertEquals(rowOf(other), table.getSelectedRow(), "Uninstall keeps the row it acts on");
    }

    @Test
    void installsAndUninstallsElsewhereUpdateTheOpenDialog() throws Exception {
        open();
        assertFalse(dialog.showsTable());

        String hello = f.install("hello");
        flush();
        assertTrue(dialog.showsTable());
        assertEquals(0, rowOf(hello));

        f.registry.uninstall(hello);
        flush();
        assertFalse(dialog.showsTable());
    }

    @Test
    void theHighlightFollowsItsAppWhenAnotherAppSortsInAboveIt() throws Exception {
        String hello = f.install("hello");
        open();
        select(hello);
        assertEquals(0, rowOf(hello));

        String earlier = f.installOffline("Aardvark");
        flush();

        assertEquals(1, rowOf(hello));
        assertEquals(rowOf(hello), table().getSelectedRow(), "still the same app");
        assertEquals("Uninstall " + hello, dialog.uninstallButton().getToolTipText());
        assertEquals(0, rowOf(earlier));
    }

    @Test
    void aCheckingRowStaysPlainAfterAnOnlineRowIsDrawn() throws Exception {
        String hello = f.install("hello");
        open();
        f.checks.runAll();
        flush();
        String checking = f.install("menu-root-apps");
        flush();
        assertEquals(
                "Checking…", table().getValueAt(rowOf(checking), ServiceAppManagerDialog.STATUS));

        statusCell(hello); // bold green, in the shared renderer
        JLabel cell = statusCell(checking);

        assertEquals(table().getForeground(), cell.getForeground());
        assertFalse(cell.getFont().isBold());
    }

    @Test
    void theCloseButtonClosesTheDialogAndStopsChecks() throws Exception {
        f.install("hello");
        open();
        JButton close = (JButton) find(dialog.getContentPane(), "close");
        assertNotNull(close);

        onEdt(close::doClick);
        // dispose() delivers the window-closed event later; let it stop the checks first.
        flush();

        assertFalse(dialog.isDisplayable());
        assertNoCheckRuns();
    }

    @Test
    void theWindowCloseButtonClosesTheDialog() throws Exception {
        f.install("hello");
        open();

        onEdt(() -> dialog.dispatchEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING)));
        flush();

        assertFalse(dialog.isDisplayable());
        assertNoCheckRuns();
    }

    @Test
    void escapeClosesTheDialogAndStopsChecks() throws Exception {
        f.install("hello");
        open();
        Object key =
                dialog.getRootPane()
                        .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                        .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));

        onEdt(
                () ->
                        dialog.getRootPane()
                                .getActionMap()
                                .get(key)
                                .actionPerformed(
                                        new ActionEvent(
                                                dialog, ActionEvent.ACTION_PERFORMED, "close")));
        flush();

        assertFalse(dialog.isDisplayable());
        assertNoCheckRuns();
    }

    @Test
    void theMenuActionOpensOneDialogBringsItBackAndReplacesItOnceClosed() throws Exception {
        ManageServiceAppsAction action =
                new ManageServiceAppsAction(
                        () ->
                                new ServiceAppManagerDialog(
                                        null,
                                        f.model(),
                                        Confirmer.OPTION_PANE,
                                        AppStoreLink.from(null),
                                        opened::add));
        assertEquals("Apps.Service Apps", action.getPreferredMenu());
        assertEquals("Manage", action.getName());

        onEdt(() -> action.actionPerformed(null));
        ServiceAppManagerDialog first = action.dialog();
        onEdt(() -> action.actionPerformed(null));

        try {
            assertSame(first, action.dialog());
            assertTrue(first.isShowing());
        } finally {
            onEdt(first::dispose);
        }

        onEdt(() -> action.actionPerformed(null));
        ServiceAppManagerDialog reopened = action.dialog();
        try {
            assertNotSame(first, reopened, "a closed dialog is replaced, not reused");
            assertTrue(reopened.isShowing());
        } finally {
            onEdt(reopened::dispose);
        }
    }

    /** Runs whatever checks were queued: cancelled ones do nothing, so no status is recorded. */
    private void assertNoCheckRuns() {
        f.checks.runAll();
        verify(f.registry, never()).setReachable(anyString(), anyBoolean());
    }

    /** A left-button mouse event; a press holds the button down. */
    private static void mouse(Component target, int id, int x, int y, int clicks) {
        target.dispatchEvent(
                new MouseEvent(
                        target,
                        id,
                        System.currentTimeMillis(),
                        id == MouseEvent.MOUSE_PRESSED ? InputEvent.BUTTON1_DOWN_MASK : 0,
                        x,
                        y,
                        clicks,
                        false,
                        MouseEvent.BUTTON1));
    }

    private static Component find(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof Container inner) {
                Component found = find(inner, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
