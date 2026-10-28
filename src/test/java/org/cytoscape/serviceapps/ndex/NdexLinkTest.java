package org.cytoscape.serviceapps.ndex;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.serviceapps.testsupport.TestNetworks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NdexLinkTest {

    private static final String UUID = "6f1b4ca6-0f2a-11ef-9a1f-005056ae23aa";

    /** A network with the UUID column CyNDEx-2 writes, set to {@code uuid}. */
    static CyNetwork withUuid(String uuid) {
        CyNetwork network = TestNetworks.withEveryColumnType();
        CyTable hidden = network.getTable(CyNetwork.class, CyNetwork.HIDDEN_ATTRS);
        hidden.createColumn(NdexLink.UUID_COLUMN, String.class, false);
        hidden.createColumn(NdexLink.MODIFICATION_COLUMN, String.class, false);
        hidden.getRow(network.getSUID()).set(NdexLink.UUID_COLUMN, uuid);
        hidden.getRow(network.getSUID()).set(NdexLink.MODIFICATION_COLUMN, "2026-10-28 09:00:00.0");
        return network;
    }

    private static NdexLink link(String base) {
        return new NdexLink(() -> base, network -> false);
    }

    @Test
    void aNetworkFromNdexGetsItsV3UrlOnTheConfiguredServer() {
        assertEquals(
                "https://www.ndexbio.org/v3/networks/" + UUID,
                link(NdexLink.DEFAULT_BASE_URL).networkUrl(withUuid(UUID)));
        assertEquals(
                "https://dev.ndexbio.org/v3/networks/" + UUID,
                link("https://dev.ndexbio.org//").networkUrl(withUuid(UUID)));
    }

    @Test
    void aBlankServerSettingFallsBackToTheDefault() {
        assertEquals(
                "https://www.ndexbio.org/v3/networks/" + UUID,
                link("  ").networkUrl(withUuid(UUID)));
    }

    @Test
    void aLocalNetworkOrNoNetworkGetsAnEmptyValue() {
        NdexLink link = link(NdexLink.DEFAULT_BASE_URL);

        assertEquals("", link.networkUrl(TestNetworks.withEveryColumnType()));
        assertEquals("", link.networkUrl(withUuid(" ")));
        assertEquals("", link.networkUrl(null));
    }

    @Test
    void anEditedNetworkGetsAnEmptyValue() {
        List<CyNetwork> asked = new ArrayList<>();
        CyNetwork network = withUuid(UUID);
        NdexLink link =
                new NdexLink(
                        () -> NdexLink.DEFAULT_BASE_URL,
                        n -> {
                            asked.add(n);
                            return true;
                        });

        assertEquals("", link.networkUrl(network));
        assertEquals(List.of(network), asked);
    }

    @Test
    void readsTheUuidCyNdex2Stored() {
        assertEquals(UUID, NdexLink.uuidOf(withUuid(UUID)));
        assertNull(NdexLink.uuidOf(TestNetworks.withEveryColumnType()));
    }
}
