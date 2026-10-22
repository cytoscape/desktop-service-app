package org.cytoscape.serviceapps.request;

/** A request can't be built from the network as it is, for a reason the user can act on. */
public class RequestException extends RuntimeException {

    public RequestException(String message) {
        super(message);
    }

    public RequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
