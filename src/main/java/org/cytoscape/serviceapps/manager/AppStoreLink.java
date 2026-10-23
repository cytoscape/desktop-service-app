package org.cytoscape.serviceapps.manager;

import java.net.URI;
import java.util.Properties;

/**
 * The App Store site Desktop uses, and the pages on it this app links to.
 *
 * <p>The site follows Desktop's own App Store setting, {@code appStoreDownloadSiteUrl1} in {@code
 * cytoscape3.props}, without trailing slashes. When the setting is absent or blank, it is the
 * public App Store. Each page is the site plus that page's path, so more pages can be added as
 * methods.
 *
 * @param site the App Store site's base URL, with no trailing slash
 */
public record AppStoreLink(String site) {

    /** Desktop's property for its first App Store download site. */
    public static final String SITE_PROPERTY = "appStoreDownloadSiteUrl1";

    public static final String DEFAULT_SITE = "https://apps.cytoscape.org";

    /** The App Store page that lists service apps. */
    public static final String SERVICE_LIST_PATH = "/apps/platform/service/";

    /**
     * @param cytoscapeProperties {@code cytoscape3.props}, or null when it isn't available
     */
    public static AppStoreLink from(Properties cytoscapeProperties) {
        String site =
                cytoscapeProperties == null ? null : cytoscapeProperties.getProperty(SITE_PROPERTY);
        if (site == null || site.isBlank()) {
            site = DEFAULT_SITE;
        }
        site = site.strip();
        while (site.endsWith("/")) {
            site = site.substring(0, site.length() - 1);
        }
        return new AppStoreLink(site);
    }

    /** The page listing every service app: the site plus {@value #SERVICE_LIST_PATH}. */
    public String getServiceList() {
        return site + SERVICE_LIST_PATH;
    }

    /** The site's host, with its port when it has one, for link text. */
    public String label() {
        try {
            String authority = URI.create(site).getAuthority();
            if (authority != null && !authority.isBlank()) {
                return authority;
            }
        } catch (IllegalArgumentException e) {
            // not a URI; show it as it is
        }
        return site;
    }
}
