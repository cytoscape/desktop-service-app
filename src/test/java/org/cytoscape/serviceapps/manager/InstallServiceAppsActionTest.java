package org.cytoscape.serviceapps.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import org.cytoscape.serviceapps.testsupport.DesktopMenus;

import static org.cytoscape.serviceapps.testsupport.DesktopMenus.onEdtAndWait;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Apps > Service Apps > Install opens the App Store's service app list in the browser. */
class InstallServiceAppsActionTest {

    private final Properties properties = new Properties();
    private final List<String> opened = new ArrayList<>();
    private final InstallServiceAppsAction install =
            new InstallServiceAppsAction(() -> AppStoreLink.from(properties), opened::add);

    @Test
    void itIsTheInstallItemUnderManageInTheServiceAppsMenu() {
        ManageServiceAppsAction manage = new ManageServiceAppsAction(() -> null);

        assertEquals("Apps.Service Apps", install.getPreferredMenu());
        assertEquals("Install", install.getName());
        assertEquals("Apps.Service Apps", manage.getPreferredMenu());
        assertEquals("Manage", manage.getName());
        assertTrue(manage.getMenuGravity() < install.getMenuGravity(), "Manage comes first");
    }

    @Test
    void withNoAppStoreSettingItOpensThePublicServiceAppList() {
        install.actionPerformed(null);

        assertEquals(List.of("https://apps.cytoscape.org/apps/platform/service/"), opened);
    }

    @Test
    void withAnAppStoreSettingItOpensThatSitesServiceAppList() {
        properties.setProperty("appStoreDownloadSiteUrl1", "https://test.apps.example.org/");

        install.actionPerformed(null);

        assertEquals(List.of("https://test.apps.example.org/apps/platform/service/"), opened);
    }

    @Test
    void theSettingIsReadOnEachClick() {
        install.actionPerformed(null);
        properties.setProperty("appStoreDownloadSiteUrl1", "http://localhost:8080");
        install.actionPerformed(null);

        assertEquals(
                List.of(
                        "https://apps.cytoscape.org/apps/platform/service/",
                        "http://localhost:8080/apps/platform/service/"),
                opened);
    }

    @Test
    void bothItemsSitInAppsServiceAppsAndLeaveNothingBehindWhenRemoved() {
        DesktopMenus desktop = new DesktopMenus();
        ManageServiceAppsAction manage = new ManageServiceAppsAction(() -> null);
        onEdtAndWait(
                () -> {
                    desktop.swing().addAction(install);
                    desktop.swing().addAction(manage);
                });

        assertEquals(
                List.of("Apps > Service Apps > Manage", "Apps > Service Apps > Install"),
                desktop.tree("Apps"));

        // As when the Desktop Service App stops: Desktop's own cleanup removes the submenu.
        onEdtAndWait(
                () -> {
                    desktop.swing().removeAction(manage);
                    desktop.swing().removeAction(install);
                });

        assertEquals(List.of(), desktop.tree("Apps"));
    }
}
