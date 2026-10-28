package org.cytoscape.serviceapps.ndex;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.events.NetworkAboutToBeDestroyedListener;
import org.cytoscape.model.events.NetworkAddedListener;
import org.cytoscape.model.events.RowSetRecord;
import org.cytoscape.model.events.RowsSetEvent;
import org.cytoscape.model.events.RowsSetListener;
import org.cytoscape.serviceapps.testsupport.TestNetworks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NdexSupportTest {

    private static final String UUID = "6f1b4ca6-0f2a-11ef-9a1f-005056ae23aa";
    private static final String URL = "https://www.ndexbio.org/v3/networks/" + UUID;

    private final CyNetworkManager networks = TestNetworks.support().getNetworkManager();

    private static NdexSupport start(CyNetworkManager networks, Properties settings) {
        return NdexSupport.start(
                networks, TestNetworks.support().getNetworkTableManager(), () -> settings);
    }

    /** The bundled {@code serviceapps.props}, as the app ships it. */
    private static Properties shippedSettings() throws IOException {
        Properties settings = new Properties();
        try (InputStream in = NdexSupport.class.getResourceAsStream("/serviceapps.props")) {
            assertNotNull(in, "serviceapps.props is bundled");
            settings.load(in);
        }
        return settings;
    }

    @Test
    void aNetworkOpenAtStartIsBaselinedSoALaterEditCounts() throws IOException {
        CyNetwork network = NdexLinkTest.withUuid(UUID);
        networks.addNetwork(network);
        NdexSupport ndex = start(networks, shippedSettings());

        network.addNode();

        assertEquals("", ndex.link().networkUrl(network));
    }

    @Test
    void theListenerIsTheTrackerTheLinkConsults() throws IOException {
        CyNetwork network = NdexLinkTest.withUuid(UUID);
        networks.addNetwork(network);
        NdexSupport ndex = start(networks, shippedSettings());
        network.addNode();
        assertEquals("", ndex.link().networkUrl(network));

        CyTable hidden = network.getTable(CyNetwork.class, CyNetwork.HIDDEN_ATTRS);
        CyRow row = hidden.getRow(network.getSUID());
        ndex.listener()
                .handleEvent(
                        new RowsSetEvent(
                                hidden,
                                List.of(
                                        new RowSetRecord(
                                                row, NdexLink.MODIFICATION_COLUMN, "t", "t"))));

        assertEquals(URL, ndex.link().networkUrl(network), "a save makes it clean again");
        assertTrue(ndex.listener() instanceof RowsSetListener);
        assertTrue(ndex.listener() instanceof NetworkAddedListener);
        assertTrue(ndex.listener() instanceof NetworkAboutToBeDestroyedListener);
    }

    @Test
    void theServerComesFromTheKeyTheShippedSettingsUse() throws IOException {
        Properties settings = shippedSettings();
        assertEquals(NdexLink.DEFAULT_BASE_URL, settings.getProperty(NdexLink.BASE_URL));
        CyNetwork network = NdexLinkTest.withUuid(UUID);
        NdexSupport ndex = start(networks, settings);
        assertEquals(URL, ndex.link().networkUrl(network));

        settings.setProperty(NdexLink.BASE_URL, "https://dev.ndexbio.org");

        assertEquals(
                "https://dev.ndexbio.org/v3/networks/" + UUID,
                ndex.link().networkUrl(network),
                "read on every run");
    }

    @Test
    void missingSettingsUseTheDefaultServer() {
        CyNetwork network = NdexLinkTest.withUuid(UUID);

        assertEquals(URL, start(networks, null).link().networkUrl(network));
        assertEquals(URL, start(networks, new Properties()).link().networkUrl(network));
    }
}
