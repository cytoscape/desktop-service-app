package org.cytoscape.serviceapps.cx;

import java.util.List;

import com.google.gson.JsonArray;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.view.model.CyNetworkView;

/**
 * Reads and writes CX2 through Cytoscape's CX Support app. An interface so tests can stand in for
 * it; {@link CxSupportBridge} is the real one.
 */
public interface Cx2Support {

    /** Whether CX Support's CX2 writer and reader are both available. */
    boolean available();

    /**
     * Writes {@code network} as CX2, with node and edge IDs from the {@code CX Element ID} column.
     *
     * @param view the network's view, for positions and style; null to write the network alone
     * @throws Cx2Exception if CX Support is missing or the export fails
     */
    JsonArray export(CyNetwork network, CyNetworkView view);

    /**
     * Imports one CX2 network as a new network collection, registers it and its view, and returns
     * it.
     *
     * @throws Cx2Exception if CX Support is missing or the CX2 can't be read
     */
    List<CyNetwork> importAndRegister(JsonArray cx2, String name);

    /** Called when {@link #available()} may have changed. */
    void addAvailabilityListener(Runnable listener);

    /** A CX2 export or import failed. */
    class Cx2Exception extends RuntimeException {
        public Cx2Exception(String message) {
            super(message);
        }

        public Cx2Exception(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
