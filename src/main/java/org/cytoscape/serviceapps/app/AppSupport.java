package org.cytoscape.serviceapps.app;

import java.util.Locale;
import java.util.Optional;

import org.cytoscape.serviceapps.model.Parameter;
import org.cytoscape.serviceapps.model.ServiceInputDefinition;
import org.cytoscape.serviceapps.model.ServiceMetadata;

/**
 * Whether Desktop can run a service app at all, and how the app's input is shaped.
 *
 * <p>Shared by menus, the Service Apps manager, the launcher and the {@code task call} command, so
 * they always agree.
 */
public final class AppSupport {

    public static final String INPUT_NODE = "node";
    public static final String INPUT_EDGE = "edge";
    public static final String INPUT_NETWORK = "network";

    public static final String SCOPE_DYNAMIC = "dynamic";
    public static final String SCOPE_SELECTED = "selected";
    public static final String SCOPE_ALL = "all";

    private AppSupport() {}

    /**
     * Why Desktop can't run this app, or empty if it can.
     *
     * @param cx2Available whether Cytoscape's CX Support app can read and write CX2
     */
    public static Optional<String> unsupportedReason(ServiceMetadata meta, boolean cx2Available) {
        if (meta == null) {
            return Optional.of("its metadata isn't available yet");
        }
        for (Parameter parameter : meta.parameters()) {
            if (parameter.type != null && Parameter.UNSUPPORTED_TYPES.contains(parameter.type)) {
                return Optional.of(
                        "it uses the parameter type '"
                                + parameter.type
                                + "', which Cytoscape Desktop doesn't support yet");
            }
        }
        ServiceInputDefinition.InputNetwork network = inputNetwork(meta);
        if (INPUT_NETWORK.equals(inputType(meta)) && network != null) {
            if ("edgelist".equalsIgnoreCase(String.valueOf(network.format))) {
                return Optional.of(
                        "it asks for the network as an edge list, which isn't supported yet");
            }
            if (!cx2Available) {
                return Optional.of(
                        "it sends networks as CX2, which needs Cytoscape's CX Support app");
            }
        }
        return Optional.empty();
    }

    /** {@code node}, {@code edge}, {@code network}, or null when the app takes no data. */
    public static String inputType(ServiceMetadata meta) {
        ServiceInputDefinition input = meta == null ? null : meta.serviceInputDefinition;
        if (input == null || input.type == null || input.type.isBlank()) {
            return null;
        }
        return input.type.strip().toLowerCase(Locale.ROOT);
    }

    /** {@code dynamic} (the default), {@code selected} or {@code all}. */
    public static String scope(ServiceMetadata meta) {
        ServiceInputDefinition input = meta == null ? null : meta.serviceInputDefinition;
        if (input == null || input.scope == null || input.scope.isBlank()) {
            return SCOPE_DYNAMIC;
        }
        return input.scope.strip().toLowerCase(Locale.ROOT);
    }

    /** The network format the app asks for, or null when it doesn't take a network. */
    public static ServiceInputDefinition.InputNetwork inputNetwork(ServiceMetadata meta) {
        return meta == null || meta.serviceInputDefinition == null
                ? null
                : meta.serviceInputDefinition.inputNetwork;
    }

    /** Whether the app's input needs a network to be open. */
    public static boolean needsNetwork(ServiceMetadata meta) {
        return inputType(meta) != null;
    }
}
