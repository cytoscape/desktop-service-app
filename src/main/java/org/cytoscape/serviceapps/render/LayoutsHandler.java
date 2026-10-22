package org.cytoscape.serviceapps.render;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.serviceapps.model.ResultAction;
import org.cytoscape.serviceapps.request.IdMapper;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.model.View;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;

/**
 * {@code updateLayouts}: moves nodes to the given positions, with {@code z} when present. Applies
 * to the target view when one is given, otherwise to every view of the network.
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/updateLayouts-Example">example</a>
 */
public class LayoutsHandler implements ActionHandler {

    private final CyNetworkViewManager viewManager;

    public LayoutsHandler(CyNetworkViewManager viewManager) {
        this.viewManager = viewManager;
    }

    @Override
    public String action() {
        return ResultAction.UPDATE_LAYOUTS;
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    @Override
    public void apply(JsonElement data, UIInfo ui, RenderReport report) {
        CyNetwork network = ui.targetNetwork();
        List<CyNetworkView> views = new ArrayList<>();
        if (ui.targetView() != null) {
            views.add(ui.targetView());
        } else {
            Collection<CyNetworkView> all = viewManager.getNetworkViews(network);
            if (all != null) {
                views.addAll(all);
            }
        }
        if (views.isEmpty()) {
            report.note("updateLayouts: the network has no view to move nodes in");
            return;
        }
        IdMapper ids = IdMapper.of(network);
        ids.ensureIds();
        int[] unknown = {0};
        Edt.run(
                () -> {
                    for (JsonElement entry : data.getAsJsonArray()) {
                        JsonObject position = entry.getAsJsonObject();
                        CyNode node = ids.node(position.get("id").getAsLong());
                        if (node == null || !network.containsNode(node)) {
                            unknown[0]++;
                            continue;
                        }
                        for (CyNetworkView view : views) {
                            View<CyNode> nodeView = view.getNodeView(node);
                            if (nodeView == null) {
                                continue;
                            }
                            nodeView.setVisualProperty(
                                    BasicVisualLexicon.NODE_X_LOCATION,
                                    position.get("x").getAsDouble());
                            nodeView.setVisualProperty(
                                    BasicVisualLexicon.NODE_Y_LOCATION,
                                    position.get("y").getAsDouble());
                            if (position.has("z") && !position.get("z").isJsonNull()) {
                                nodeView.setVisualProperty(
                                        BasicVisualLexicon.NODE_Z_LOCATION,
                                        position.get("z").getAsDouble());
                            }
                        }
                    }
                    for (CyNetworkView view : views) {
                        view.updateView();
                    }
                });
        if (unknown[0] > 0) {
            report.note("updateLayouts skipped " + unknown[0] + " ID(s) not in the network");
        }
    }
}
