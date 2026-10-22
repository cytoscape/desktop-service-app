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
        List<Position> positions = positions(data);
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
                    for (Position position : positions) {
                        CyNode node = ids.node(position.id());
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
                                    BasicVisualLexicon.NODE_X_LOCATION, position.x());
                            nodeView.setVisualProperty(
                                    BasicVisualLexicon.NODE_Y_LOCATION, position.y());
                            if (position.z() != null) {
                                nodeView.setVisualProperty(
                                        BasicVisualLexicon.NODE_Z_LOCATION, position.z());
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

    private record Position(long id, double x, double y, Double z) {}

    /** Reads every position before any node moves, so bad data changes nothing. */
    private static List<Position> positions(JsonElement data) {
        if (!data.isJsonArray()) {
            throw new IllegalArgumentException(
                    "updateLayouts data is not a list of node positions");
        }
        List<Position> positions = new ArrayList<>();
        for (JsonElement entry : data.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                throw new IllegalArgumentException(
                        "updateLayouts has an entry that isn't a position");
            }
            JsonObject position = entry.getAsJsonObject();
            try {
                JsonElement z = position.get("z");
                positions.add(
                        new Position(
                                position.get("id").getAsLong(),
                                position.get("x").getAsDouble(),
                                position.get("y").getAsDouble(),
                                z == null || z.isJsonNull() ? null : z.getAsDouble()));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "updateLayouts position " + position + " needs a numeric id, x and y");
            }
        }
        return positions;
    }
}
