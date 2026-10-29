package org.cytoscape.serviceapps.testsupport;

import org.cytoscape.serviceapps.client.HttpTransport;
import org.cytoscape.serviceapps.ndex.NdexLink;

/** NDEx links for tests that don't look at the {@code ndexUUID} value. */
public final class TestNdex {

    private TestNdex() {}

    /**
     * Links to the default server, treats every network as unedited, and finds every network there.
     */
    public static NdexLink link() {
        return new NdexLink(() -> NdexLink.DEFAULT_BASE_URL, network -> false, FOUND);
    }

    /** An NDEx server that holds every network asked for. */
    public static final HttpTransport FOUND =
            (method, uri, body) -> new HttpTransport.Response(200, "{}");
}
