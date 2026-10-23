package org.cytoscape.serviceapps.manager;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.FixtureServerExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
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
        open(AppStoreLink.from(new java.util.Properties()));
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

    private String tooltip(int row, int column) throws Exception {
        String[] text = new String[1];
        onEdt(
                () -> {
                    Component cell =
                            table().prepareRenderer(
                                            table().getCellRenderer(row, column), row, column);
                    text[0] = ((JComponent) cell).getToolTipText();
                });
        return text[0];
    }

    private boolean enabled(int row, int column) throws Exception {
        boolean[] enabled = new boolean[1];
        onEdt(
                () ->
                        enabled[0] =
                                table().prepareRenderer(
                                                table().getCellRenderer(row, column), row, column)
                                        .isEnabled());
        return enabled[0];
    }

    @Test
    void withNoAppsTheDialogShowsTheCenteredLabelInsteadOfATable() throws Exception {
        open();

        assertEquals("Service Apps", dialog.getTitle());
        assertFalse(dialog.isModal());
        assertEquals("No Service Apps installed", dialog.emptyLabel().getText());
        assertEquals(
                javax.swing.SwingConstants.CENTER, dialog.emptyLabel().getHorizontalAlignment());
        assertTrue(dialog.emptyLabel().isVisible());
        assertFalse(dialog.showsTable());
    }

    /** The "Go to <host> and choose a service app to install" line, as displayed. */
    private String appStoreHint() {
        java.awt.Container hint =
                (java.awt.Container) find(dialog.getContentPane(), "appStoreHint");
        StringBuilder text = new StringBuilder();
        for (Component part : hint.getComponents()) {
            text.append(((javax.swing.JLabel) part).getText());
        }
        return text.toString();
    }

    /** The link shows {@code label}, underlines on hover, and opens {@code url} on click. */
    private void assertLink(String label, String url) throws Exception {
        assertEquals("Go to " + label + " and choose a service app to install", appStoreHint());
        javax.swing.JLabel link = dialog.appStoreLink();
        assertEquals(label, link.getText());
        assertEquals(url, link.getToolTipText());
        // Styled like Desktop's own links: link color, hand cursor, underline on hover only.
        assertEquals(ServiceAppManagerDialog.linkColor(), link.getForeground());
        assertEquals(java.awt.Cursor.HAND_CURSOR, link.getCursor().getType());

        onEdt(() -> mouse(link, java.awt.event.MouseEvent.MOUSE_ENTERED));
        assertEquals("<html><u>" + label + "</u></html>", link.getText());
        onEdt(() -> mouse(link, java.awt.event.MouseEvent.MOUSE_EXITED));
        assertEquals(label, link.getText());
        assertTrue(opened.isEmpty(), "hovering opens nothing");

        onEdt(() -> mouse(link, java.awt.event.MouseEvent.MOUSE_CLICKED));

        assertEquals(List.of(url), opened);
    }

    @Test
    void withNoAppStoreSettingTheLinkGoesToThePublicAppStoresServiceApps() throws Exception {
        open(AppStoreLink.from(new java.util.Properties()));

        assertLink("apps.cytoscape.org", "https://apps.cytoscape.org/apps/platform/service/");
    }

    @Test
    void withAnAppStoreSettingTheLinkGoesToThatSitesServiceApps() throws Exception {
        java.util.Properties properties = new java.util.Properties();
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
                ((javax.swing.JLabel) find(dialog.getContentPane(), "commandHint")).getText());
        javax.swing.JTextField command =
                (javax.swing.JTextField) find(dialog.getContentPane(), "installCommand");
        assertEquals("serviceapps install url=<service URL>", command.getText());
        assertFalse(command.isEditable(), "selectable for copying, not editable");
    }

    @Test
    void appsAreListedWithTheFourColumnsAndLiveStatus() throws Exception {
        String hello = f.install("hello");
        String noMenu = f.install("menu-root-missing");
        String offline = f.installOffline("Offline app");
        open();

        assertTrue(dialog.showsTable());
        assertEquals(
                List.of("Name", "Status", "Invoke", "Uninstall"),
                List.of(
                        table().getColumnName(0),
                        table().getColumnName(1),
                        table().getColumnName(2),
                        table().getColumnName(3)));
        assertEquals(3, table().getRowCount());
        assertEquals("Checking…", table().getValueAt(rowOf(hello), ServiceAppManagerDialog.STATUS));

        f.checks.runAll();
        flush();

        assertEquals("Online", table().getValueAt(rowOf(hello), ServiceAppManagerDialog.STATUS));
        assertEquals("Online", table().getValueAt(rowOf(noMenu), ServiceAppManagerDialog.STATUS));
        assertEquals("Offline", table().getValueAt(rowOf(offline), ServiceAppManagerDialog.STATUS));
        assertTrue(
                tooltip(rowOf(hello), ServiceAppManagerDialog.NAME)
                        .contains(f.fixture.url("hello")));
        assertFalse(enabled(rowOf(offline), ServiceAppManagerDialog.INVOKE));
        assertEquals(
                "Can't run: service unreachable",
                tooltip(rowOf(offline), ServiceAppManagerDialog.INVOKE));
    }

    @Test
    void tickingInvokeLaunchesTheAppOnceAndUnticks() throws Exception {
        String hello = f.install("hello");
        open();
        int row = rowOf(hello);
        assertTrue(table().isCellEditable(row, ServiceAppManagerDialog.INVOKE));

        onEdt(() -> table().setValueAt(true, row, ServiceAppManagerDialog.INVOKE));
        flush();

        verify(f.launcher, times(1)).launch(hello);
        assertEquals(false, table().getValueAt(row, ServiceAppManagerDialog.INVOKE));
        assertTrue(dialog.isDisplayable());
    }

    @Test
    void anUnsupportedAppsInvokeIsDisabledWithTheReason() throws Exception {
        String name = f.install("unsupported-file");
        open();
        int row = rowOf(name);

        assertFalse(table().isCellEditable(row, ServiceAppManagerDialog.INVOKE));
        assertFalse(enabled(row, ServiceAppManagerDialog.INVOKE));
        assertTrue(
                tooltip(row, ServiceAppManagerDialog.INVOKE)
                        .startsWith("Can't run: it uses the parameter type 'file'"));
    }

    @Test
    void cancellingUninstallChangesNothing() throws Exception {
        String hello = f.install("hello");
        open();
        confirm = false;

        onEdt(() -> table().setValueAt(true, rowOf(hello), ServiceAppManagerDialog.UNINSTALL));
        flush();

        assertEquals(List.of("Uninstall " + hello + "? Are you sure?"), questions);
        verify(f.registry, never()).uninstall(anyString());
        assertEquals(1, table().getRowCount());
        assertEquals(false, table().getValueAt(0, ServiceAppManagerDialog.UNINSTALL));
    }

    @Test
    void confirmingUninstallRemovesTheRowAndTheLastOneShowsTheLabel() throws Exception {
        String hello = f.install("hello");
        String other = f.install("menu-root-apps");
        open();
        confirm = true;

        onEdt(() -> table().setValueAt(true, rowOf(hello), ServiceAppManagerDialog.UNINSTALL));
        flush();

        verify(f.registry).uninstall(hello);
        assertEquals(1, table().getRowCount());
        assertTrue(dialog.isDisplayable());

        onEdt(() -> table().setValueAt(true, rowOf(other), ServiceAppManagerDialog.UNINSTALL));
        flush();

        assertFalse(dialog.showsTable());
        assertTrue(dialog.emptyLabel().isVisible());
        assertTrue(dialog.isDisplayable());
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
    void theCloseButtonClosesTheDialogAndStopsChecks() throws Exception {
        f.install("hello");
        open();
        JButton close = (JButton) find(dialog.getContentPane(), "close");
        assertNotNull(close);

        onEdt(close::doClick);

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
    void escapeClosesTheDialog() throws Exception {
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
    }

    @Test
    void theMenuActionOpensOneDialogAndBringsItBack() throws Exception {
        ManageServiceAppsAction action =
                new ManageServiceAppsAction(
                        () ->
                                new ServiceAppManagerDialog(
                                        null,
                                        f.model(),
                                        Confirmer.OPTION_PANE,
                                        AppStoreLink.from(null),
                                        opened::add));
        assertEquals("Tools.Service Apps", action.getPreferredMenu());
        assertEquals("Manage", action.getName());

        onEdt(() -> action.actionPerformed(null));
        ServiceAppManagerDialog first = action.dialog();
        onEdt(() -> action.actionPerformed(null));

        try {
            assertTrue(first == action.dialog());
            assertTrue(first.isShowing());
        } finally {
            onEdt(first::dispose);
        }
    }

    /** Runs whatever checks were queued: cancelled ones do nothing, so no status is recorded. */
    private void assertNoCheckRuns() {
        f.checks.runAll();
        verify(f.registry, never()).setReachable(anyString(), anyBoolean());
    }

    private static void mouse(Component target, int id) {
        target.dispatchEvent(
                new java.awt.event.MouseEvent(
                        target,
                        id,
                        System.currentTimeMillis(),
                        0,
                        2,
                        2,
                        1,
                        false,
                        java.awt.event.MouseEvent.BUTTON1));
    }

    private static Component find(java.awt.Container container, String name) {
        for (Component child : container.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof java.awt.Container inner) {
                Component found = find(inner, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
