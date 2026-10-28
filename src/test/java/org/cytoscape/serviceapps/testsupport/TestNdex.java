package org.cytoscape.serviceapps.testsupport;

import org.cytoscape.serviceapps.ndex.NdexLink;

/** NDEx links for tests that don't look at the {@code ndexUUID} value. */
public final class TestNdex {

    private TestNdex() {}

    /** Links to the default server and treats every network as unedited. */
    public static NdexLink link() {
        return new NdexLink(() -> NdexLink.DEFAULT_BASE_URL, network -> false);
    }
}
