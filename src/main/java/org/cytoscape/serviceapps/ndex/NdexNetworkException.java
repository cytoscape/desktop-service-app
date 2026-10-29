package org.cytoscape.serviceapps.ndex;

/** The NDEx server can't confirm a network a run would name, with a reason the user can read. */
public class NdexNetworkException extends RuntimeException {

    public NdexNetworkException(String message) {
        super(message);
    }
}
