package org.cytoscape.serviceapps.manager;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The App Store site from Desktop's setting, and its service app list page. */
class AppStoreLinkTest {

    private static Properties withSite(String site) {
        Properties properties = new Properties();
        properties.setProperty("appStoreDownloadSiteUrl1", site);
        return properties;
    }

    @Test
    void withoutTheSettingItIsThePublicAppStore() {
        for (Properties properties : new Properties[] {null, new Properties()}) {
            AppStoreLink link = AppStoreLink.from(properties);

            assertEquals("https://apps.cytoscape.org", link.site());
            assertEquals(
                    "https://apps.cytoscape.org/apps/platform/service/", link.getServiceList());
            assertEquals("apps.cytoscape.org", link.label());
        }
    }

    @Test
    void aBlankSettingCountsAsAbsent() {
        assertEquals(
                "https://apps.cytoscape.org/apps/platform/service/",
                AppStoreLink.from(withSite("   ")).getServiceList());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "https://test.apps.example.org,       https://test.apps.example.org, test.apps.example.org",
        "https://test.apps.example.org/,      https://test.apps.example.org, test.apps.example.org",
        "https://test.apps.example.org///,    https://test.apps.example.org, test.apps.example.org",
        "'  https://test.apps.example.org/ ', https://test.apps.example.org, test.apps.example.org",
        "http://localhost:8080/store/,        http://localhost:8080/store,   localhost:8080",
        "https://apps.cytoscape.org/,         https://apps.cytoscape.org,    apps.cytoscape.org"
    })
    void theSettingIsTheSiteAndTheServiceListIsOnIt(String setting, String site, String label) {
        AppStoreLink link = AppStoreLink.from(withSite(setting));

        assertEquals(site, link.site());
        assertEquals(site + "/apps/platform/service/", link.getServiceList());
        assertEquals(label, link.label());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "apps-store,        apps-store", // no scheme: no authority, shown as set
        "'http://a b/',     http://a b", // not a URI, shown as set
        "file:///srv/store, file:///srv/store" // a URI without a host
    })
    void aSiteWithNoHostIsLabelledAsItIsSet(String setting, String label) {
        assertEquals(label, AppStoreLink.from(withSite(setting)).label());
    }
}
