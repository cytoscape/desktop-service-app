package org.cytoscape.serviceapps.render;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.serviceapps.cx.Cx2Support;
import org.cytoscape.serviceapps.model.ResultAction;

/**
 * {@code addNetworks}: imports each returned CX2 network as a new network collection with a view,
 * like File &gt; Import of a CX2 file. The first becomes the current network.
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/addNetworks-Example">example</a>
 */
public class AddNetworksHandler implements ActionHandler {

    private final Cx2Support cx2;
    private final CyApplicationManager applicationManager;

    public AddNetworksHandler(Cx2Support cx2, CyApplicationManager applicationManager) {
        this.cx2 = cx2;
        this.applicationManager = applicationManager;
    }

    @Override
    public String action() {
        return ResultAction.ADD_NETWORKS;
    }

    @Override
    public boolean needsNetwork() {
        return false;
    }

    @Override
    public void apply(JsonElement data, UIInfo ui, RenderReport report) {
        if (!data.isJsonArray()) {
            throw new IllegalArgumentException("addNetworks data is not a list of CX2 networks");
        }
        for (JsonElement network : data.getAsJsonArray()) {
            if (!network.isJsonArray()) {
                throw new IllegalArgumentException(
                        "addNetworks has an entry that isn't a CX2 network");
            }
        }
        List<CyNetwork> added = new ArrayList<>();
        for (JsonElement network : data.getAsJsonArray()) {
            added.addAll(cx2.importAndRegister(network.getAsJsonArray(), "Service app result"));
        }
        if (added.isEmpty()) {
            report.note("addNetworks returned no networks");
            return;
        }
        Edt.run(() -> applicationManager.setCurrentNetwork(added.get(0)));
        report.note("added " + added.size() + " network(s)");
    }
}
