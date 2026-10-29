package org.cytoscape.serviceapps.ndex;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.serviceapps.client.HttpTransport;

/**
 * The value of an {@code ndexUUID} parameter: the NDEx URL of the network a run works on, as
 * Cytoscape Web sends it, {@code <server>/v3/networks/<uuid>}.
 *
 * <p>The UUID is the one CyNDEx-2 stores in the network's hidden table when the network is imported
 * from or saved to NDEx. Before a run sends a URL, {@link #value} asks that server for the
 * network's summary. The value is empty, with a note saying why, for a network with no valid UUID,
 * one edited since that import or save, and one the server doesn't confirm. A service reads an
 * empty value as a network that lives only in Desktop, and still receives the network itself as the
 * request's data.
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/parameters">parameters</a>
 */
public class NdexLink {

    /** CyNDEx-2's hidden network column holding the network's NDEx UUID. */
    public static final String UUID_COLUMN = "NDEx UUID";

    /** CyNDEx-2's hidden network column, written on every import from and save to NDEx. */
    public static final String MODIFICATION_COLUMN = "NDEx Modification Timestamp";

    /** The {@code serviceapps.props} key naming the NDEx server the URL points at. */
    public static final String BASE_URL = "ndex.base.url";

    public static final String DEFAULT_BASE_URL = "https://www.ndexbio.org";

    /** How every note for an empty value starts, in the run's completion message. */
    public static final String EMPTY_NOTE = "NDEx address left empty: ";

    /**
     * An {@code ndexUUID} value, and the note saying why it is empty.
     *
     * @param url the NDEx URL, or empty
     * @param note the completion-message note for an empty value; null when the URL is sent
     */
    public record Value(String url, String note) {

        static Value sent(String url) {
            return new Value(url, null);
        }

        static Value empty(String reason) {
            return new Value("", EMPTY_NOTE + reason);
        }
    }

    private final Supplier<String> baseUrl;
    private final Predicate<CyNetwork> edited;
    private final HttpTransport http;

    /**
     * @param baseUrl the NDEx server, read on every call so a changed setting applies at once
     * @param edited whether a network changed since its last import from or save to NDEx
     * @param http sends the summary request
     */
    public NdexLink(Supplier<String> baseUrl, Predicate<CyNetwork> edited, HttpTransport http) {
        this.baseUrl = baseUrl;
        this.edited = edited;
        this.http = http;
    }

    /** The NDEx URL of {@code network}, without asking the server; empty as {@link #value}. */
    public String networkUrl(CyNetwork network) {
        return local(network).url();
    }

    /**
     * The value to send for {@code network}: its NDEx URL once the server confirms the network, or
     * empty with the reason. Asks the server only when there is a URL to send.
     */
    public Value value(CyNetwork network) {
        Value value = local(network);
        return value.note() == null ? confirmed(network, value.url()) : value;
    }

    /** The value from what Desktop knows: the network, its UUID and whether it was edited. */
    private Value local(CyNetwork network) {
        if (network == null) {
            return Value.empty("no network is open");
        }
        String stored = storedUuid(network);
        if (stored == null || stored.isBlank()) {
            return Value.empty(
                    "network " + name(network) + " was never imported from or saved to NDEx");
        }
        String uuid = canonical(stored);
        if (uuid == null) {
            return Value.empty(
                    "network "
                            + name(network)
                            + " has an NDEx UUID that isn't valid: \""
                            + stored.strip()
                            + "\"");
        }
        if (edited.test(network)) {
            return Value.empty(
                    "network "
                            + name(network)
                            + " was edited in Desktop since it was last imported from or saved to"
                            + " NDEx");
        }
        return Value.sent(url(baseUrl.get(), uuid));
    }

    /** {@code url}, once the server answers success for the network's summary. */
    private Value confirmed(CyNetwork network, String url) {
        String server = server(baseUrl.get());
        String name = name(network);
        URI summary;
        try {
            summary = URI.create(url + "/summary");
        } catch (IllegalArgumentException e) {
            return Value.empty(
                    "the " + BASE_URL + " setting, " + server + ", is not a web address");
        }
        HttpTransport.Response response;
        try {
            response = http.send("GET", summary, null);
        } catch (IOException | IllegalArgumentException e) {
            return Value.empty(
                    "NDEx at "
                            + server
                            + " could not be reached for network "
                            + name
                            + ": "
                            + (e.getMessage() == null
                                    ? e.getClass().getSimpleName()
                                    : e.getMessage()));
        }
        if (response.isSuccess()) {
            return Value.sent(url);
        }
        return switch (response.status()) {
            case 404 -> Value.empty(
                    "network "
                            + name
                            + " does not exist on "
                            + server
                            + ", and only networks loaded from "
                            + server
                            + " are sent with their NDEx address");
            case 401, 403 -> Value.empty(
                    "network "
                            + name
                            + " is not public on "
                            + server
                            + ", and only public networks are sent with their NDEx address");
            default -> Value.empty(
                    "NDEx at "
                            + server
                            + " answered HTTP "
                            + response.status()
                            + " for network "
                            + name);
        };
    }

    private static String name(CyNetwork network) {
        return "\"" + network.getRow(network).get(CyNetwork.NAME, String.class) + "\"";
    }

    /**
     * The UUID CyNDEx-2 stored for {@code network}, in lower case, or null. Any app or session file
     * can write the column, so a value that is a UUID in canonical form is the only one used; the
     * network counts as local otherwise.
     */
    public static String uuidOf(CyNetwork network) {
        return canonical(storedUuid(network));
    }

    /** The text in {@code network}'s {@code NDEx UUID} column, or null when it has none. */
    private static String storedUuid(CyNetwork network) {
        CyTable hidden = network.getTable(CyNetwork.class, CyNetwork.HIDDEN_ATTRS);
        if (hidden == null || hidden.getColumn(UUID_COLUMN) == null) {
            return null;
        }
        CyRow row = hidden.getRow(network.getSUID());
        return row == null ? null : row.get(UUID_COLUMN, String.class);
    }

    /** {@code text} as a lower-case UUID, or null when it is anything else. */
    static String canonical(String text) {
        if (text == null) {
            return null;
        }
        String lower = text.strip().toLowerCase(Locale.ROOT);
        try {
            // fromString accepts short groups such as "1-2-3-4-5"; the round trip keeps them out.
            return UUID.fromString(lower).toString().equals(lower) ? lower : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** {@code <base>/v3/networks/<uuid>}, with any trailing slash on the base dropped. */
    static String url(String base, String uuid) {
        return server(base) + "/v3/networks/" + uuid;
    }

    /** The NDEx server {@code base} names, without a trailing slash; the default when blank. */
    static String server(String base) {
        String server = base == null || base.isBlank() ? DEFAULT_BASE_URL : base.strip();
        return server.replaceAll("/+$", "");
    }
}
