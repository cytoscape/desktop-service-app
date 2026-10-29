package org.cytoscape.serviceapps.ndex;

import java.time.Duration;
import java.util.Properties;
import java.util.function.Supplier;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNetworkTableManager;
import org.cytoscape.serviceapps.client.HttpTransport;
import org.cytoscape.serviceapps.client.JdkHttpTransport;

/**
 * The app's NDEx link and the edit tracker behind it, built together so the link always consults
 * the tracker Desktop delivers events to.
 */
public final class NdexSupport {

    /** How long the NDEx check waits, to connect and again to read the answer. */
    static final Duration CHECK_TIMEOUT = Duration.ofSeconds(3);

    private static final HttpTransport CHECK = new JdkHttpTransport(CHECK_TIMEOUT, CHECK_TIMEOUT);

    private final NetworkEditTracker tracker;
    private final NdexLink link;

    private NdexSupport(NetworkEditTracker tracker, NdexLink link) {
        this.tracker = tracker;
        this.link = link;
    }

    /**
     * Takes a baseline of every network open now, so edits count from this moment.
     *
     * @param networks the networks Desktop has open
     * @param tables finds the network a changed table belongs to
     * @param settings the app's {@code serviceapps.props}, read on every run
     */
    public static NdexSupport start(
            CyNetworkManager networks,
            CyNetworkTableManager tables,
            Supplier<Properties> settings) {
        NetworkEditTracker tracker = new NetworkEditTracker(tables);
        for (CyNetwork network : networks.getNetworkSet()) {
            tracker.markSaved(network);
        }
        return new NdexSupport(
                tracker, new NdexLink(() -> baseUrl(settings.get()), tracker::edited, CHECK));
    }

    /** The NDEx server named in {@code settings}, or the default. */
    static String baseUrl(Properties settings) {
        return settings == null
                ? NdexLink.DEFAULT_BASE_URL
                : settings.getProperty(NdexLink.BASE_URL, NdexLink.DEFAULT_BASE_URL);
    }

    /** The tracker, to register for Desktop's row, network-added and network-destroyed events. */
    public NetworkEditTracker listener() {
        return tracker;
    }

    /** The link that gives a run the network's NDEx URL, or an empty value for a local network. */
    public NdexLink link() {
        return link;
    }
}
