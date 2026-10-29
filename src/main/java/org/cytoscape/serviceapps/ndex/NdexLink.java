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
 * from or saved to NDEx. The value is empty for a network with no UUID, and for one edited since
 * that import or save, whose NDEx copy is older than what the service receives. A service reads an
 * empty value as a network that lives only in Desktop.
 *
 * <p>Before a run sends a URL, {@link #checkedNetworkUrl} asks that server for the network's
 * summary, so a network from another server, or one deleted there, fails the run with a reason.
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

    private final Supplier<String> baseUrl;
    private final Predicate<CyNetwork> edited;
    private final Supplier<HttpTransport> http;

    /**
     * @param baseUrl the NDEx server, read on every call so a changed setting applies at once
     * @param edited whether a network changed since its last import from or save to NDEx
     * @param http sends the summary request; asked for on every check, for the current timeouts
     */
    public NdexLink(
            Supplier<String> baseUrl, Predicate<CyNetwork> edited, Supplier<HttpTransport> http) {
        this.baseUrl = baseUrl;
        this.edited = edited;
        this.http = http;
    }

    /** The NDEx URL of {@code network}, or empty when the service should treat it as local. */
    public String networkUrl(CyNetwork network) {
        if (network == null) {
            return "";
        }
        String uuid = uuidOf(network);
        if (uuid == null || edited.test(network)) {
            return "";
        }
        return url(baseUrl.get(), uuid);
    }

    /**
     * {@link #networkUrl}, once the NDEx server confirms it holds that network. An empty value goes
     * unchecked.
     *
     * @throws NdexNetworkException when the server answers anything but success for the network's
     *     summary, or can't be reached
     */
    public String checkedNetworkUrl(CyNetwork network) {
        String url = networkUrl(network);
        if (url.isEmpty()) {
            return url;
        }
        String server = server(baseUrl.get());
        String name = network.getRow(network).get(CyNetwork.NAME, String.class);
        URI summary;
        try {
            summary = URI.create(url + "/summary");
        } catch (IllegalArgumentException e) {
            throw new NdexNetworkException(
                    unable(name)
                            + " because the "
                            + BASE_URL
                            + " setting, "
                            + server
                            + ", is not a web address.");
        }
        HttpTransport.Response response;
        try {
            response = http.get().send("GET", summary, null);
        } catch (IOException | IllegalArgumentException e) {
            throw new NdexNetworkException(
                    unable(name)
                            + " because NDEx at "
                            + server
                            + " could not be reached: "
                            + e.getMessage());
        }
        if (response.isSuccess()) {
            return url;
        }
        switch (response.status()) {
            case 404 -> throw new NdexNetworkException(
                    unable(name)
                            + " because it does not exist on "
                            + server
                            + ". Only networks loaded from "
                            + server
                            + " will work with service apps.");
            case 401, 403 -> throw new NdexNetworkException(
                    unable(name)
                            + " because it is not public on "
                            + server
                            + ". Only public networks loaded from "
                            + server
                            + " will work with service apps.");
            default -> throw new NdexNetworkException(
                    unable(name)
                            + " because NDEx at "
                            + server
                            + " answered HTTP "
                            + response.status()
                            + " when asked for it.");
        }
    }

    private static String unable(String name) {
        return "Unable to use network \"" + name + "\"";
    }

    /**
     * The UUID CyNDEx-2 stored for {@code network}, in lower case, or null. Any app or session file
     * can write the column, so a value that is a UUID in canonical form is the only one used; the
     * network counts as local otherwise.
     */
    public static String uuidOf(CyNetwork network) {
        CyTable hidden = network.getTable(CyNetwork.class, CyNetwork.HIDDEN_ATTRS);
        if (hidden == null || hidden.getColumn(UUID_COLUMN) == null) {
            return null;
        }
        CyRow row = hidden.getRow(network.getSUID());
        String stored = row == null ? null : row.get(UUID_COLUMN, String.class);
        return canonical(stored);
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
