package org.cytoscape.serviceapps.call;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.model.ResultAction;
import org.cytoscape.serviceapps.model.ServiceMetadata;

/**
 * A service app's outputs for {@code describe}: what each result action it declares changes in
 * Desktop, and which {@code uiInput} fields choose where. Follows the result table in the user
 * manual.
 */
public final class ResultActionEffects {

    /** One action's effect and the {@code uiInput} fields that target it. */
    record Effect(String text, List<String> targets) {}

    private static final List<String> NETWORK = List.of(UiTargetResolver.NETWORK_ID);

    static final Map<String, Effect> EFFECTS =
            Map.of(
                    ResultAction.UPDATE_SELECTION,
                    new Effect(
                            "Selects exactly the listed nodes and edges in the target network and"
                                    + " deselects everything else.",
                            NETWORK),
                    ResultAction.UPDATE_LAYOUTS,
                    new Effect(
                            "Moves the listed nodes in the target view, or in every view of the"
                                    + " target network.",
                            List.of(UiTargetResolver.NETWORK_ID, UiTargetResolver.NETWORK_VIEW_ID)),
                    ResultAction.UPDATE_TABLES,
                    new Effect(
                            "Adds or fills in node or edge columns of the target network; rows for"
                                    + " IDs not in the network are skipped.",
                            NETWORK),
                    ResultAction.UPDATE_NETWORK,
                    new Effect(
                            "Updates the target network in place: attributes, new nodes and edges,"
                                    + " and removal of sent elements missing from the result; its"
                                    + " style is kept.",
                            NETWORK),
                    ResultAction.ADD_NETWORKS,
                    new Effect(
                            "Opens each returned network as a new network collection with a view.",
                            List.of()),
                    ResultAction.OPEN_URL,
                    new Effect("Opens the returned web address in the browser.", List.of()),
                    ResultAction.ADD_TABLES,
                    new Effect("Not supported in Desktop yet: skipped with a note.", NETWORK));

    static final Effect UNKNOWN =
            new Effect(
                    "Not a result action Desktop knows: a run that returns it fails before anything"
                            + " is applied.",
                    List.of());

    private ResultActionEffects() {}

    /** {@code [{action, effect, targets}]}, one per action the app declares, in its order. */
    public static JsonArray of(ServiceMetadata meta) {
        JsonArray actions = new JsonArray();
        for (String action : meta.cyWebActions()) {
            Effect effect = EFFECTS.getOrDefault(action, UNKNOWN);
            JsonObject entry = new JsonObject();
            entry.addProperty("action", action);
            entry.addProperty("effect", effect.text());
            entry.add("targets", Schemas.strings(effect.targets()));
            actions.add(entry);
        }
        return actions;
    }
}
