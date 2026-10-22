package org.cytoscape.serviceapps.app;

import java.util.Optional;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTableUtil;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.InstalledServiceApp;

/**
 * Whether a service app can run right now, and Desktop's {@code ENABLE_FOR} rule for its menu
 * entry. The launcher applies the same rules, so a menu entry and the manager's Invoke agree.
 *
 * <p>For scope {@code selected}, the app needs the selection it works on: at least one node for
 * node input, at least one edge for edge input, and anything for network input.
 */
public final class RunPrecondition {

    /** Desktop's {@code ENABLE_FOR} values used here. */
    public static final String ALWAYS = "always";

    public static final String NETWORK = "network";
    public static final String SELECTED_NODES = "selectedNodes";
    public static final String SELECTED_EDGES = "selectedEdges";
    public static final String SELECTED_NODES_OR_EDGES = "selectedNodesOrEdges";

    private RunPrecondition() {}

    /** The {@code ENABLE_FOR} value for a runnable app's menu entry. */
    public static String enableFor(ServiceMetadata meta) {
        String type = AppSupport.inputType(meta);
        if (type == null) {
            return ALWAYS;
        }
        if (!AppSupport.SCOPE_SELECTED.equals(AppSupport.scope(meta))) {
            return NETWORK;
        }
        switch (type) {
            case AppSupport.INPUT_NODE:
                return SELECTED_NODES;
            case AppSupport.INPUT_EDGE:
                return SELECTED_EDGES;
            default:
                return SELECTED_NODES_OR_EDGES;
        }
    }

    /**
     * Why the app can't run right now, as a message for the user, or empty if it can.
     *
     * @param current the current network, or null
     */
    public static Optional<String> check(
            InstalledServiceApp app,
            ServiceMetadata meta,
            CyNetwork current,
            boolean cx2Available) {
        String name = app.name();
        Optional<String> unsupported = AppSupport.unsupportedReason(meta, cx2Available);
        if (unsupported.isPresent()) {
            return Optional.of(
                    name + " can't run in Cytoscape Desktop: " + unsupported.get() + ".");
        }
        if (!app.reachable()) {
            return Optional.of(
                    "The service for "
                            + name
                            + " can't be reached right now. Check your connection, or open"
                            + " Tools > Service Apps > Manage Service Apps… to check it again.");
        }
        String type = AppSupport.inputType(meta);
        if (type == null) {
            return Optional.empty();
        }
        if (current == null) {
            return Optional.of(name + " works on the current network. Open a network first.");
        }
        if (!AppSupport.SCOPE_SELECTED.equals(AppSupport.scope(meta))) {
            return Optional.empty();
        }
        boolean nodes = !CyTableUtil.getSelectedNodes(current).isEmpty();
        boolean edges = !CyTableUtil.getSelectedEdges(current).isEmpty();
        switch (type) {
            case AppSupport.INPUT_NODE:
                return nodes
                        ? Optional.empty()
                        : Optional.of(
                                "Select at least one node in the current network first. "
                                        + name
                                        + " works on the selected nodes.");
            case AppSupport.INPUT_EDGE:
                return edges
                        ? Optional.empty()
                        : Optional.of(
                                "Select at least one edge in the current network first. "
                                        + name
                                        + " works on the selected edges.");
            default:
                return nodes || edges
                        ? Optional.empty()
                        : Optional.of(
                                "Select at least one node or edge in the current network first. "
                                        + name
                                        + " works on the selected part of the network.");
        }
    }
}
