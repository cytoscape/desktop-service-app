package org.cytoscape.serviceapps.render;

import com.google.gson.JsonElement;

/** Applies one kind of result action to Desktop. */
public interface ActionHandler {

    /** The action name, such as {@code updateSelection}. */
    String action();

    /** Whether the action needs a target network. */
    boolean needsNetwork();

    /**
     * @throws RuntimeException if the action can't be applied; the renderer reports it and goes on
     */
    void apply(JsonElement data, UIInfo ui, RenderReport report);
}
