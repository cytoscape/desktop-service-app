package org.cytoscape.serviceapps.model;

import java.util.List;

import com.google.gson.JsonElement;

/**
 * One step of a task result: what to do, and the data to do it with.
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/result">result</a>
 */
public class ResultAction {

    public static final String ADD_NETWORKS = "addNetworks";
    public static final String UPDATE_NETWORK = "updateNetwork";
    public static final String ADD_TABLES = "addTables";
    public static final String UPDATE_TABLES = "updateTables";
    public static final String UPDATE_LAYOUTS = "updateLayouts";
    public static final String UPDATE_SELECTION = "updateSelection";
    public static final String OPEN_URL = "openURL";

    /**
     * The actions the spec defines, in the order of the <a
     * href="https://github.com/cytoscape/cytoscape-web/wiki/cyWebActions">cyWebActions</a> page.
     */
    public static final List<String> ALL =
            List.of(
                    ADD_NETWORKS,
                    UPDATE_NETWORK,
                    ADD_TABLES,
                    UPDATE_TABLES,
                    UPDATE_LAYOUTS,
                    UPDATE_SELECTION,
                    OPEN_URL);

    public String action;
    public JsonElement data;

    public ResultAction() {}

    public ResultAction(String action, JsonElement data) {
        this.action = action;
        this.data = data;
    }
}
