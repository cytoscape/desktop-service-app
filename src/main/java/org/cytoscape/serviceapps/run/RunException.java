package org.cytoscape.serviceapps.run;

/** A run failed; the message says why, for the user. */
public class RunException extends RuntimeException {

    public RunException(String message) {
        super(message);
    }

    public RunException(String message, Throwable cause) {
        super(message, cause);
    }
}
