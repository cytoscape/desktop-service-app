package org.cytoscape.serviceapps.request;

import java.util.Set;

/**
 * The node and edge IDs a request sent to the service. {@code updateNetwork} may only remove
 * elements listed here, so a run on a selection never deletes the rest of the network.
 */
public record SentIds(Set<Long> nodes, Set<Long> edges) {

    public static final SentIds NONE = new SentIds(Set.of(), Set.of());

    public SentIds {
        nodes = Set.copyOf(nodes);
        edges = Set.copyOf(edges);
    }
}
