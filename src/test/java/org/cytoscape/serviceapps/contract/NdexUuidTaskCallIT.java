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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An {@code ndexUUID} parameter through {@code serviceapps task call}, against real Cytoscape in
 * Docker: the edit tracker, its listener registration, the {@code ndex.base.url} setting and the
 * check that the NDEx server holds the network, as Desktop wires them. Every run completes; when
 * the value is empty, the completion message says why.
 *
 * <p>{@code ndex.base.url} points at the fixture's stand-in NDEx server, which holds {@link
 * FixtureServer#NDEX_NETWORK} as public and {@link FixtureServer#NDEX_PRIVATE_NETWORK} as private.
 * CyNDEx-2 records an import or save by writing the network's hidden {@code NDEx UUID} and {@code
 * NDEx Modification Timestamp} columns; this test writes them the same way, with {@code network set
 * attribute namespace=hidden}, so it runs offline.
 */
@ExtendWith(DesktopExtension.class)
class NdexUuidTaskCallIT {

    private static final String SCENARIO = "param-ndex-uuid";
    private static final String NO_INPUT = "param-ndex-uuid-no-input";
    private static final String MISSING = "0b5e0f6c-2c6a-11ef-9a1f-005056ae23aa";
    private static final String APPLIED = "Applied updateLayouts";

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

    /** What one run sent and said. */
    private record Run(String value, String message, List<String> ndexRequests) {}

    /** Runs {@code scenario} and returns its {@code ndexUUID} value and completion message. */
    private Run run(String scenario, Ui ui) {
        int mark = fixture.requests().size();
        Outcome outcome = h.run(scenario, ui);
        assertTrue(outcome.succeeded(), outcome.message());
        List<FixtureServer.RecordedRequest> since =
                fixture.requests().subList(mark, fixture.requests().size());
        JsonObject received =
                since.stream()
                        .filter(r -> r.method().equals("POST") && r.path().equals("/" + scenario))
                        .map(r -> JsonParser.parseString(r.body()).getAsJsonObject())
                        .reduce((first, second) -> second)
                        .orElseThrow();
        return new Run(
                received.getAsJsonObject("parameters").get("NDEx network").getAsString(),
                outcome.message(),
                since.stream()
                        .filter(r -> r.path().startsWith("/ndex/"))
                        .map(r -> r.method() + " " + r.path())
                        .toList());
    }

    private Run run() {
        return run(SCENARIO, Ui.NO_CURRENT_VIEW);
    }

    private static String note(String reason) {
        return APPLIED + ". Notes: " + NdexLink.EMPTY_NOTE + reason;
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
    void aNetworkTheServerHoldsIsSentWithItsUrlAndNoNote() throws IOException {
        cyNdex2Writes(NdexLink.UUID_COLUMN, FixtureServer.NDEX_NETWORK);

        Run run = run();

        assertEquals(ndex + "/v3/networks/" + FixtureServer.NDEX_NETWORK, run.value());
        assertEquals(APPLIED, run.message());
        assertEquals(
                List.of("GET /ndex/v3/networks/" + FixtureServer.NDEX_NETWORK + "/summary"),
                run.ndexRequests());
    }

    @Test
    void aNetworkNeverOnNdexIsSentEmptyWithItsNote() {
        Run run = run();

        assertEquals("", run.value());
        assertEquals(
                note("network \"target\" was never imported from or saved to NDEx"), run.message());
        assertEquals(List.of(), run.ndexRequests());
    }

    @Test
    void anInvalidUuidIsSentEmptyWithItsNote() throws IOException {
        cyNdex2Writes(NdexLink.UUID_COLUMN, "not a uuid");

        Run run = run();

        assertEquals("", run.value());
        assertEquals(
                note("network \"target\" has an NDEx UUID that isn't valid: \"not a uuid\""),
                run.message());
        assertEquals(List.of(), run.ndexRequests());
    }

    @Test
    void anEditedNetworkIsSentEmptyWithItsNoteUntilSavedAgain() throws IOException {
        cyNdex2Writes(NdexLink.UUID_COLUMN, FixtureServer.NDEX_NETWORK);
        addNode();

        Run edited = run();

        assertEquals("", edited.value());
        assertEquals(
                note(
                        "network \"target\" was edited in Desktop since it was last imported from"
                                + " or saved to NDEx"),
                edited.message());
        assertEquals(List.of(), edited.ndexRequests());

        cyNdex2Writes(NdexLink.MODIFICATION_COLUMN, "2026-10-28 12:00:00.0");
        Run saved = run();

        assertEquals(ndex + "/v3/networks/" + FixtureServer.NDEX_NETWORK, saved.value());
        assertEquals(APPLIED, saved.message());
    }

    @Test
    void aNetworkTheServerDoesntHoldIsSentEmptyWithItsNote() throws IOException {
        cyNdex2Writes(NdexLink.UUID_COLUMN, MISSING);

        Run run = run();

        assertEquals("", run.value());
        assertEquals(
                note(
                        "network \"target\" does not exist on "
                                + ndex
                                + ", and only networks loaded from "
                                + ndex
                                + " are sent with their NDEx address"),
                run.message());
    }

    @Test
    void aPrivateNetworkIsSentEmptyWithItsNote() throws IOException {
        cyNdex2Writes(NdexLink.UUID_COLUMN, FixtureServer.NDEX_PRIVATE_NETWORK);

        Run run = run();

        assertEquals("", run.value());
        assertEquals(
                note(
                        "network \"target\" is not public on "
                                + ndex
                                + ", and only public networks are sent with their NDEx address"),
                run.message());
    }

    @Test
    void anUnreachableServerIsSentEmptyWithItsNote() throws IOException {
        cyNdex2Writes(NdexLink.UUID_COLUMN, FixtureServer.NDEX_NETWORK);
        String unreachable = "http://127.0.0.1:9/ndex";
        setNdexBaseUrl(unreachable);

        Run run = run();

        assertEquals("", run.value());
        String prefix =
                note("NDEx at " + unreachable + " could not be reached for network \"target\": ");
        assertTrue(run.message().startsWith(prefix), run.message());
        assertTrue(run.message().length() > prefix.length(), "the reason is named");
    }

    @Test
    void aServerSettingThatIsntAnAddressIsSentEmptyWithItsNote() throws IOException {
        cyNdex2Writes(NdexLink.UUID_COLUMN, FixtureServer.NDEX_NETWORK);
        setNdexBaseUrl("ndex bio");

        Run run = run();

        assertEquals("", run.value());
        assertEquals(
                note("the ndex.base.url setting, ndex bio, is not a web address"), run.message());
    }

    @Test
    void anAppThatTakesNoDataWithNoNetworkOpenIsSentEmptyWithItsNote() {
        Run run = run(NO_INPUT, Ui.NO_NETWORK);

        assertEquals("", run.value());
        assertEquals(
                "Applied openURL. Notes: " + NdexLink.EMPTY_NOTE + "no network is open",
                run.message());
    }
}
