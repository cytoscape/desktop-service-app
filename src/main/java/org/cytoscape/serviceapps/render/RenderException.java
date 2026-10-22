package org.cytoscape.serviceapps.render;

/** A result can't be applied at all, such as an action that needs a network when none is open. */
public class RenderException extends RuntimeException {

    public RenderException(String message) {
        super(message);
    }
}
