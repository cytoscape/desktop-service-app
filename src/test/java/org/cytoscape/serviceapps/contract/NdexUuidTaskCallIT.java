package org.cytoscape.serviceapps.contract;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.serviceapps.contract.ContractHarness.Outcome;
import org.cytoscape.serviceapps.contract.ContractHarness.Ui;
import org.cytoscape.serviceapps.desktop.CyRest;
import org.cytoscape.serviceapps.desktop.DesktopContainer;
import org.cytoscape.serviceapps.desktop.DesktopExtension;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.ndex.NdexLink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An {@code ndexUUID} parameter through {@code serviceapps task call}, against real Cytoscape in
 * Docker: the edit tracker, its listener registration, the {@code ndex.base.url} setting and the
 * check that the NDEx server holds the network, as Desktop wires them.
 *
 * <p>{@code ndex.base.url} points at the fixture's stand-in NDEx server, which holds {@link
 * FixtureServer#NDEX_NETWORK} alone. CyNDEx-2 records an import or save by writing the network's
 * hidden {@code NDEx UUID} and {@code NDEx Modification Timestamp} columns; this test writes them
 * the same way, with {@code network set attribute namespace=hidden}, so it runs offline.
 */
@ExtendWith(DesktopExtension.class)
class NdexUuidTaskCallIT {

    private static final String SCENARIO = "param-ndex-uuid";
    private static final String MISSING = "0b5e0f6c-2c6a-11ef-9a1f-005056ae23aa";

    private final FixtureServer fixture;
    private final CyRest cyrest;
    private final DockerTaskCallHarness h;
    private final String ndex;

    NdexUuidTaskCallIT(DesktopContainer desktop, FixtureServer fixture) {
        this.fixture = fixture;
        this.cyrest = desktop.cyrest();
        this.h = new DockerTaskCallHarness(desktop, fixture, false);
        this.ndex = DesktopExtension.containerUrl(fixture, "ndex");
    }

    @BeforeEach
    void loadNetworksAndPointAtTheStandInNdex() throws IOException {
        h.reset();
        setNdexBaseUrl(ndex);
        fixture.clearRequests();
    }

    @AfterEach
    void restoreTheNdexServer() throws IOException {
        setNdexBaseUrl(NdexLink.DEFAULT_BASE_URL);
    }

    private void setNdexBaseUrl(String url) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("key", NdexLink.BASE_URL);
        body.addProperty("value", url);
        cyrest.rest("PUT", "/v1/properties/serviceapps.props/" + NdexLink.BASE_URL, body);
    }

    /** The method and path of each request the fixture received since {@code mark}. */
    private List<String> requestsSince(int mark) {
        List<FixtureServer.RecordedRequest> all = fixture.requests();
        return all.subList(mark, all.size()).stream()
                .map(r -> r.method() + " " + r.path())
                .toList();
    }

    /** Runs the app on the target network and returns the {@code ndexUUID} value it was sent. */
    private String sentValue() {
        Outcome outcome = h.run(SCENARIO, Ui.NO_CURRENT_VIEW);
        assertTrue(outcome.succeeded(), outcome.message());
        List<FixtureServer.RecordedRequest> posts =
                fixture.requests().stream()
                        .filter(r -> r.method().equals("POST") && r.path().equals("/" + SCENARIO))
                        .toList();
        JsonObject received =
                JsonParser.parseString(posts.get(posts.size() - 1).body()).getAsJsonObject();
        return received.getAsJsonObject("parameters").get("NDEx network").getAsString();
    }

    /** Writes a hidden network column as CyNDEx-2 does on an import or save. */
    private void cyNdex2Writes(String column, String value) throws IOException {
        long network = h.targetSuid();
        cyrest.post(
                "network",
                "create attribute",
                Map.of(
                        "network",
                        "SUID:" + network,
                        "namespace",
                        "hidden",
                        "column",
                        column,
                        "type",
                        "string"));
        CyRest.Reply reply =
                cyrest.post(
                        "network",
                        "set attribute",
                        Map.of(
                                "network",
                                "SUID:" + network,
                                "namespace",
                                "hidden",
                                "columnList",
                                column,
                                "valueList",
                                value));
        assertTrue(reply.ok(), reply.toString());
    }

    private void addNode() throws IOException {
        JsonArray names = new JsonArray();
        names.add("added");
        cyrest.rest("POST", "/v1/networks/" + h.targetSuid() + "/nodes", names);
    }

    @Test
    void aLocalNetworkSendsAnEmptyValueWithoutAskingNdex() {
        int mark = fixture.requests().size();

        assertEquals("", sentValue());
        assertTrue(
                requestsSince(mark).stream().noneMatch(r -> r.contains("/ndex/")),
                requestsSince(mark).toString());
    }

    @Test
    void aNetworkTheServerHoldsSendsItsUrlUntilEditedAndAgainOnceSaved() throws IOException {
        String url = ndex + "/v3/networks/" + FixtureServer.NDEX_NETWORK;
        cyNdex2Writes(NdexLink.UUID_COLUMN, FixtureServer.NDEX_NETWORK);

        int mark = fixture.requests().size();
        assertEquals(url, sentValue(), "imported from NDEx");
        assertTrue(
                requestsSince(mark)
                        .contains(
                                "GET /ndex/v3/networks/" + FixtureServer.NDEX_NETWORK + "/summary"),
                requestsSince(mark).toString());

        addNode();
        assertEquals("", sentValue(), "edited in Desktop");

        cyNdex2Writes(NdexLink.MODIFICATION_COLUMN, "2026-10-28 12:00:00.0");
        assertEquals(url, sentValue(), "saved to NDEx again");
    }

    @Test
    void aNetworkTheServerDoesntHoldFailsTheRunNamingTheNetworkAndTheServer() throws IOException {
        cyNdex2Writes(NdexLink.UUID_COLUMN, MISSING);
        int mark = fixture.requests().size();

        Outcome outcome = h.run(SCENARIO, Ui.NO_CURRENT_VIEW);

        assertFalse(outcome.succeeded(), outcome.message());
        assertEquals(
                "Unable to use network \"target\" because it does not exist on "
                        + ndex
                        + ". Only networks loaded from "
                        + ndex
                        + " will work with service apps.",
                outcome.message());
        assertFalse(
                requestsSince(mark).contains("POST /" + SCENARIO),
                "the service gets nothing: " + requestsSince(mark));
    }
}
