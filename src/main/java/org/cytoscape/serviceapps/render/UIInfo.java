package org.cytoscape.serviceapps.render;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.serviceapps.request.SentIds;
import org.cytoscape.view.model.CyNetworkView;

/**
 * Where a run's results go, fixed when the run starts.
 *
 * @param targetNetwork the network network-level actions change; null when none was open
 * @param targetView the one view layout results apply to; null for every view of the network
 * @param sent the IDs the request sent, which bound what {@code updateNetwork} may remove
 */
public record UIInfo(CyNetwork targetNetwork, CyNetworkView targetView, SentIds sent) {}
