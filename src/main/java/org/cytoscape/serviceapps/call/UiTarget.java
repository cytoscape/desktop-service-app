package org.cytoscape.serviceapps.call;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.view.model.CyNetworkView;

/**
 * Where a {@code task call} applies its result, fixed when the call starts.
 *
 * @param network the target network, or null when none was given and none is active
 * @param view the one view to update, or null for every view of the network
 */
public record UiTarget(CyNetwork network, CyNetworkView view) {}
