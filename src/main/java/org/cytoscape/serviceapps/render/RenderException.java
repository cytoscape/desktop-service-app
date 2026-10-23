package org.cytoscape.serviceapps.render;

/**
 * Applying a result failed. The message is the one the user sees on both entry paths, starting with
 * {@link #FAILED}.
 */
public class RenderException extends RuntimeException {

    /** How every failure to apply a result starts, on both entry paths. */
    public static final String FAILED = "Rendering failed";

    public RenderException(String message) {
        super(message);
    }
}
