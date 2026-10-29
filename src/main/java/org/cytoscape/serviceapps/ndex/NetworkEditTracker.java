package org.cytoscape.serviceapps.ndex;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkTableManager;
import org.cytoscape.model.events.NetworkAboutToBeDestroyedEvent;
import org.cytoscape.model.events.NetworkAboutToBeDestroyedListener;
import org.cytoscape.model.events.NetworkAddedEvent;
import org.cytoscape.model.events.NetworkAddedListener;
import org.cytoscape.model.events.RowSetRecord;
import org.cytoscape.model.events.RowsSetEvent;
import org.cytoscape.model.events.RowsSetListener;

/**
 * Whether a network carrying an NDEx UUID changed since it was last imported from or saved to NDEx.
 *
 * <p>Keeps a {@link NetworkFingerprint} of each such network, taken when CyNDEx-2 writes its UUID
 * or modification timestamp, when the network is added to Desktop, or the first time it is asked
 * about. A network is edited when its fingerprint now differs from that one. Comparing content
 * keeps the answer independent of the order Desktop delivers its change events in.
 *
 * <p>Edits count from the moment the app starts. A session reopened from a file starts clean.
 */
public class NetworkEditTracker
        implements RowsSetListener, NetworkAddedListener, NetworkAboutToBeDestroyedListener {

    private final CyNetworkTableManager tables;

    /** The fingerprint each network had at its last import or save, by SUID. */
    private final Map<Long, String> saved = new ConcurrentHashMap<>();

    /**
     * @param tables finds the network a changed table belongs to
     */
    public NetworkEditTracker(CyNetworkTableManager tables) {
        this.tables = tables;
    }

    /** Whether {@code network} differs from what was last imported from or saved to NDEx. */
    public boolean edited(CyNetwork network) {
        String now = NetworkFingerprint.of(network);
        String before = saved.putIfAbsent(network.getSUID(), now);
        return before != null && !before.equals(now);
    }

    /** Records {@code network} as matching its NDEx copy, when it has one. */
    public void markSaved(CyNetwork network) {
        if (NdexLink.uuidOf(network) != null) {
            saved.put(network.getSUID(), NetworkFingerprint.of(network));
        }
    }

    /** CyNDEx-2 wrote a UUID or timestamp: the network was just imported or saved. */
    @Override
    public void handleEvent(RowsSetEvent event) {
        for (RowSetRecord record : event.getPayloadCollection()) {
            String column = record.getColumn();
            if (NdexLink.UUID_COLUMN.equals(column)
                    || NdexLink.MODIFICATION_COLUMN.equals(column)) {
                CyNetwork network = tables.getNetworkForTable(event.getSource());
                if (network != null) {
                    markSaved(network);
                }
                return;
            }
        }
    }

    @Override
    public void handleEvent(NetworkAddedEvent event) {
        markSaved(event.getNetwork());
    }

    @Override
    public void handleEvent(NetworkAboutToBeDestroyedEvent event) {
        saved.remove(event.getNetwork().getSUID());
    }
}
