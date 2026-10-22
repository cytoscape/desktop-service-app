package org.cytoscape.serviceapps.render;

import com.google.gson.JsonElement;

import org.cytoscape.serviceapps.model.ResultAction;

/**
 * {@code addTables}: not defined yet in the spec ("to be added in future"), and a no-op in
 * Cytoscape Web, so it is skipped with a note.
 */
public class AddTablesHandler implements ActionHandler {

    @Override
    public String action() {
        return ResultAction.ADD_TABLES;
    }

    @Override
    public boolean needsNetwork() {
        return false;
    }

    @Override
    public void apply(JsonElement data, UIInfo ui, RenderReport report) {
        report.note("addTables is not supported yet and was skipped");
    }
}
