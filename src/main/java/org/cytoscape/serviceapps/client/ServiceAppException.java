package org.cytoscape.serviceapps.client;

/**
 * A call to a service app failed: it answered with a non-2xx status, sent something that isn't the
 * JSON the spec describes, or could not be reached.
 */
public class ServiceAppException extends RuntimeException {

    /** {@link #httpStatus()} when no response was received. */
    public static final int NO_RESPONSE = 0;

    private final int httpStatus;

    public ServiceAppException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public ServiceAppException(int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /** The HTTP status the service answered with, or {@link #NO_RESPONSE}. */
    public int httpStatus() {
        return httpStatus;
    }
}
