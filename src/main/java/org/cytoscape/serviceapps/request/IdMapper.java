package org.cytoscape.serviceapps.request;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;

/**
 * Maps nodes and edges to the IDs a service sees, and back.
 *
 * <p>Uses the same IDs as Cytoscape's CX2 export: the hidden {@code CX Element ID} column of the
 * network collection's root. {@link #ensureIds()} fills in missing IDs with the export's own rule
 * (the element's SUID when the root has no IDs yet, otherwise one more than the highest), so table
 * input, network input and results all agree.
 */
public final class IdMapper {

    /** The hidden column Cytoscape's CX Support app keeps CX element IDs in. */
    public static final String CX_ID_COLUMN = "CX Element ID";

    private final CyRootNetwork root;

    private IdMapper(CyRootNetwork root) {
        this.root = root;
    }

    /** The mapper for {@code network}'s collection. */
    public static IdMapper of(CyNetwork network) {
        if (network instanceof CySubNetwork sub) {
            return new IdMapper(sub.getRootNetwork());
        }
        if (network instanceof CyRootNetwork rootNetwork) {
            return new IdMapper(rootNetwork);
        }
        throw new IllegalArgumentException("not a Cytoscape network: " + network);
    }

    /** Gives every node and edge in the collection an ID, keeping the ones it has. */
    public void ensureIds() {
        populate(CyNode.class, root.getNodeList());
        populate(CyEdge.class, root.getEdgeList());
    }

    /** The ID a service sees for {@code element}, assigning IDs first if needed. */
    public long id(CyIdentifiable element) {
        Class<? extends CyIdentifiable> type =
                element instanceof CyNode ? CyNode.class : CyEdge.class;
        Long id = table(type).getRow(element.getSUID()).get(CX_ID_COLUMN, Long.class);
        if (id == null) {
            ensureIds();
            id = table(type).getRow(element.getSUID()).get(CX_ID_COLUMN, Long.class);
        }
        return id;
    }

    /** The node of the collection with this ID, or null. */
    public CyNode node(long id) {
        Long suid = suid(CyNode.class, id);
        return suid == null ? null : root.getNode(suid);
    }

    /** The edge of the collection with this ID, or null. */
    public CyEdge edge(long id) {
        Long suid = suid(CyEdge.class, id);
        return suid == null ? null : root.getEdge(suid);
    }

    /** Records the ID a service used for a node or edge Desktop just created. */
    public void assign(CyIdentifiable element, long id) {
        Class<? extends CyIdentifiable> type =
                element instanceof CyNode ? CyNode.class : CyEdge.class;
        table(type).getRow(element.getSUID()).set(CX_ID_COLUMN, id);
    }

    private Long suid(Class<? extends CyIdentifiable> type, long id) {
        Collection<CyRow> rows = table(type).getMatchingRows(CX_ID_COLUMN, id);
        return rows.isEmpty() ? null : rows.iterator().next().get(CyIdentifiable.SUID, Long.class);
    }

    private void populate(
            Class<? extends CyIdentifiable> type, List<? extends CyIdentifiable> elements) {
        CyTable table = table(type);
        List<Long> existing = table.getColumn(CX_ID_COLUMN).getValues(Long.class);
        Long counter = existing.stream().filter(Objects::nonNull).max(Long::compare).orElse(null);
        boolean hadIds = counter != null;
        for (CyIdentifiable element : elements) {
            CyRow row = table.getRow(element.getSUID());
            if (row.get(CX_ID_COLUMN, Long.class) == null) {
                if (hadIds) {
                    row.set(CX_ID_COLUMN, ++counter);
                } else {
                    row.set(CX_ID_COLUMN, element.getSUID());
                }
            }
        }
    }

    private CyTable table(Class<? extends CyIdentifiable> type) {
        CyTable table = root.getTable(type, CyNetwork.HIDDEN_ATTRS);
        if (table.getColumn(CX_ID_COLUMN) == null) {
            table.createColumn(CX_ID_COLUMN, Long.class, false);
        }
        return table;
    }
}
