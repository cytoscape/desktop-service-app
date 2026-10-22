package org.cytoscape.serviceapps.render;

/**
 * Applying a result failed. The message is the one the user sees on both entry paths, starting with
 * "Rendering failed".
 */
public class RenderException extends RuntimeException {

    public RenderException(String message) {
        super(message);
    }
}
