package org.cytoscape.serviceapps.ndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.serviceapps.client.HttpTransport;
import org.cytoscape.serviceapps.testsupport.TestNdex;
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
        return new NdexLink(() -> base, network -> false, TestNdex.FOUND);
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
                        },
                        TestNdex.FOUND);

        assertEquals("", link.networkUrl(network));
        assertEquals(List.of(network), asked);
    }

    @Test
    void aValueThatIsNotACanonicalUuidCountsAsLocal() {
        NdexLink link = link(NdexLink.DEFAULT_BASE_URL);

        for (String stored :
                List.of(
                        "x/../../other?y",
                        UUID + "/extra",
                        "1-2-3-4-5",
                        "6f1b4ca6-0f2a-11ef-9a1f-005056ae23aaa",
                        "not a uuid")) {
            assertEquals("", link.networkUrl(withUuid(stored)), stored);
            assertNull(NdexLink.uuidOf(withUuid(stored)), stored);
        }
    }

    @Test
    void anUpperCaseUuidIsSentInLowerCase() {
        assertEquals(
                "https://www.ndexbio.org/v3/networks/" + UUID,
                link(NdexLink.DEFAULT_BASE_URL).networkUrl(withUuid(UUID.toUpperCase())));
    }

    @Test
    void readsTheUuidCyNdex2Stored() {
        assertEquals(UUID, NdexLink.uuidOf(withUuid(UUID)));
        assertNull(NdexLink.uuidOf(TestNetworks.withEveryColumnType()));
    }

    /**
     * A link to {@code https://ndex.example} whose server answers {@code status}, recording URLs.
     */
    private static NdexLink answering(int status, List<String> asked) {
        return new NdexLink(
                () -> "https://ndex.example/",
                network -> false,
                (method, uri, body) -> {
                    asked.add(method + " " + uri);
                    return new HttpTransport.Response(status, "{}");
                });
    }

    private static CyNetwork named(String name, String uuid) {
        CyNetwork network = withUuid(uuid);
        network.getRow(network).set(CyNetwork.NAME, name);
        return network;
    }

    private static NdexLink.Value empty(String reason) {
        return new NdexLink.Value("", NdexLink.EMPTY_NOTE + reason);
    }

    @Test
    void aNetworkTheServerHoldsIsSentWithItsUrlAndNoNote() {
        List<String> asked = new ArrayList<>();

        assertEquals(
                new NdexLink.Value("https://ndex.example/v3/networks/" + UUID, null),
                answering(200, asked).value(withUuid(UUID)));
        assertEquals(List.of("GET https://ndex.example/v3/networks/" + UUID + "/summary"), asked);
    }

    @Test
    void noNetworkOpenIsEmptyWithItsNoteAndNoServerCall() {
        List<String> asked = new ArrayList<>();

        assertEquals(empty("no network is open"), answering(200, asked).value(null));
        assertEquals(List.of(), asked);
    }

    @Test
    void aNetworkNeverOnNdexIsEmptyWithItsNoteAndNoServerCall() {
        List<String> asked = new ArrayList<>();
        CyNetwork local = TestNetworks.withEveryColumnType();
        local.getRow(local).set(CyNetwork.NAME, "Local");

        assertEquals(
                empty("network \"Local\" was never imported from or saved to NDEx"),
                answering(200, asked).value(local));
        assertEquals(
                empty("network \"Blank\" was never imported from or saved to NDEx"),
                answering(200, asked).value(named("Blank", " ")));
        assertEquals(List.of(), asked);
    }

    @Test
    void anInvalidUuidIsEmptyWithItsNoteAndNoServerCall() {
        List<String> asked = new ArrayList<>();

        assertEquals(
                empty("network \"Odd\" has an NDEx UUID that isn't valid: \"not a uuid\""),
                answering(200, asked).value(named("Odd", " not a uuid ")));
        assertEquals(List.of(), asked);
    }

    @Test
    void anEditedNetworkIsEmptyWithItsNoteAndNoServerCall() {
        List<String> asked = new ArrayList<>();
        NdexLink link =
                new NdexLink(
                        () -> "https://ndex.example",
                        network -> true,
                        (method, uri, body) -> {
                            asked.add(uri.toString());
                            return new HttpTransport.Response(200, "{}");
                        });

        assertEquals(
                empty(
                        "network \"PPI\" was edited in Desktop since it was last imported from or"
                                + " saved to NDEx"),
                link.value(named("PPI", UUID)));
        assertEquals(List.of(), asked);
    }

    @Test
    void aNetworkTheServerDoesntHoldIsEmptyWithItsNote() {
        assertEquals(
                empty(
                        "network \"PPI\" does not exist on https://ndex.example, and only networks"
                                + " loaded from https://ndex.example are sent with their NDEx"
                                + " address"),
                answering(404, new ArrayList<>()).value(named("PPI", UUID)));
    }

    @Test
    void aPrivateNetworkIsEmptyWithItsNote() {
        for (int status : new int[] {401, 403}) {
            assertEquals(
                    empty(
                            "network \"PPI\" is not public on https://ndex.example, and only"
                                    + " public networks are sent with their NDEx address"),
                    answering(status, new ArrayList<>()).value(named("PPI", UUID)),
                    "HTTP " + status);
        }
    }

    @Test
    void anyOtherAnswerIsEmptyWithANoteNamingTheStatus() {
        assertEquals(
                empty("NDEx at https://ndex.example answered HTTP 503 for network \"PPI\""),
                answering(503, new ArrayList<>()).value(named("PPI", UUID)));
    }

    @Test
    void anUnreachableServerIsEmptyWithItsNote() {
        NdexLink link =
                new NdexLink(
                        () -> "https://ndex.example",
                        network -> false,
                        (method, uri, body) -> {
                            throw new IOException("connect timed out");
                        });

        assertEquals(
                empty(
                        "NDEx at https://ndex.example could not be reached for network \"PPI\":"
                                + " connect timed out"),
                link.value(named("PPI", UUID)));
    }

    @Test
    void aServerSettingThatIsntAnAddressIsEmptyWithItsNoteAndNoServerCall() {
        List<String> asked = new ArrayList<>();
        NdexLink link =
                new NdexLink(
                        () -> "ndex bio",
                        network -> false,
                        (method, uri, body) -> {
                            asked.add(uri.toString());
                            return new HttpTransport.Response(200, "{}");
                        });

        assertEquals(
                empty("the ndex.base.url setting, ndex bio, is not a web address"),
                link.value(named("PPI", UUID)));
        assertEquals(List.of(), asked);
    }
}
