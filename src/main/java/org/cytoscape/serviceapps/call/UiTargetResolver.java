package org.cytoscape.serviceapps.call;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;

/**
 * Turns {@code task call}'s {@code uiInput} into the Desktop targets of the result actions, before
 * anything is sent to the service.
 *
 * <ul>
 *   <li>Both fields are optional: {@code networkId} (a network SUID) and {@code networkViewId} (a
 *       view SUID).
 *   <li>{@code networkViewId} alone sets the network to the view's network.
 *   <li>Given together, the view must belong to the network.
 *   <li>Omitted: the active network and every one of its views.
 *   <li>An ID that doesn't exist is rejected.
 * </ul>
 */
public class UiTargetResolver {

    public static final String NETWORK_ID = "networkId";
    public static final String NETWORK_VIEW_ID = "networkViewId";

    private final CyNetworkManager networks;
    private final CyNetworkViewManager views;
    private final CyApplicationManager applications;

    public UiTargetResolver(
            CyNetworkManager networks,
            CyNetworkViewManager views,
            CyApplicationManager applications) {
        this.networks = networks;
        this.views = views;
        this.applications = applications;
    }

    /**
     * @param uiInput the argument as text; null or blank means every field is omitted
     * @throws InvalidInputException naming each field that was rejected
     */
    public UiTarget resolve(String uiInput) {
        JsonObject json = JsonArgument.object(uiInput, "uiInput");
        List<String> problems = new ArrayList<>();
        for (String key : json.keySet()) {
            if (!Set.of(NETWORK_ID, NETWORK_VIEW_ID).contains(key)) {
                problems.add(
                        "unknown field '"
                                + key
                                + "'; uiInput takes only "
                                + NETWORK_ID
                                + " and "
                                + NETWORK_VIEW_ID);
            }
        }
        Long networkId = suid(json, NETWORK_ID, problems);
        Long viewId = suid(json, NETWORK_VIEW_ID, problems);
        if (!problems.isEmpty()) {
            throw new InvalidInputException("uiInput", problems);
        }

        CyNetwork network = null;
        if (networkId != null) {
            network = networks.getNetwork(networkId);
            if (network == null) {
                problems.add(NETWORK_ID + ": no network has SUID " + networkId);
            }
        }
        CyNetworkView view = null;
        if (viewId != null) {
            view = view(viewId);
            if (view == null) {
                problems.add(NETWORK_VIEW_ID + ": no network view has SUID " + viewId);
            } else if (network != null && view.getModel() != network) {
                problems.add(
                        NETWORK_VIEW_ID
                                + ": view "
                                + viewId
                                + " is a view of network "
                                + view.getModel().getSUID()
                                + ", not of network "
                                + networkId);
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidInputException("uiInput", problems);
        }
        if (view != null) {
            return new UiTarget(view.getModel(), view);
        }
        if (network != null) {
            return new UiTarget(network, null);
        }
        return new UiTarget(applications.getCurrentNetwork(), null);
    }

    private CyNetworkView view(long suid) {
        for (CyNetworkView view : views.getNetworkViewSet()) {
            if (view.getSUID() == suid) {
                return view;
            }
        }
        return null;
    }

    private static Long suid(JsonObject json, String key, List<String> problems) {
        JsonElement value = json.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            if (value.isJsonPrimitive()) {
                return Long.parseLong(value.getAsString().strip());
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        problems.add(key + ": must be a SUID (a whole number), not " + value);
        return null;
    }

    /** {@code uiInput}'s JSON Schema, the same for every service app. */
    public static JsonObject schema() {
        JsonObject properties = new JsonObject();
        properties.add(
                NETWORK_ID,
                Schemas.property(
                        "integer",
                        "SUID of the network the result actions apply to (updateNetwork,"
                                + " updateTables, updateLayouts, updateSelection). Default: the"
                                + " active network. Input data is also read from it."));
        properties.add(
                NETWORK_VIEW_ID,
                Schemas.property(
                        "integer",
                        "SUID of the one network view updateLayouts moves nodes in. Default: every"
                                + " view of the target network. Given alone, the target network is"
                                + " this view's network; given with networkId, it must be a view"
                                + " of that network."));
        return Schemas.root(
                "uiInput: optional Desktop targets for the result actions. An ID that doesn't exist"
                        + " is rejected before anything is sent. openURL needs no target, and"
                        + " addNetworks always opens new network collections.",
                properties,
                List.of());
    }
}
