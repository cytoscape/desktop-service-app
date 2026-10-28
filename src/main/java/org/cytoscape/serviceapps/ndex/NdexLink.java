package org.cytoscape.serviceapps.ndex;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;

/**
 * The value of an {@code ndexUUID} parameter: the NDEx URL of the network a run works on, as
 * Cytoscape Web sends it, {@code <server>/v3/networks/<uuid>}.
 *
 * <p>The UUID is the one CyNDEx-2 stores in the network's hidden table when the network is imported
 * from or saved to NDEx. The value is empty for a network with no UUID, and for one edited since
 * that import or save, whose NDEx copy is older than what the service receives. A service reads an
 * empty value as a network that lives only in Desktop.
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

    /**
     * @param baseUrl the NDEx server, read on every call so a changed setting applies at once
     * @param edited whether a network changed since its last import from or save to NDEx
     */
    public NdexLink(Supplier<String> baseUrl, Predicate<CyNetwork> edited) {
        this.baseUrl = baseUrl;
        this.edited = edited;
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
        String server = base == null || base.isBlank() ? DEFAULT_BASE_URL : base.strip();
        return server.replaceAll("/+$", "") + "/v3/networks/" + uuid;
    }
}
