package org.cytoscape.serviceapps.ndex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;

/**
 * A digest of what a user can edit in a network: its nodes, its edges, and every value in its node
 * and edge tables. Two networks with the same content give the same fingerprint.
 *
 * <p>Selection is left out, so selecting nodes leaves the fingerprint as it was. So are the hidden
 * tables, where Desktop and its apps keep their own bookkeeping, such as CX element IDs.
 */
public final class NetworkFingerprint {

    private NetworkFingerprint() {}

    public static String of(CyNetwork network) {
        MessageDigest digest = sha256();
        List<CyNode> nodes = sorted(network.getNodeList());
        List<CyEdge> edges = sorted(network.getEdgeList());
        for (CyNode node : nodes) {
            add(digest, "n" + node.getSUID());
        }
        for (CyEdge edge : edges) {
            add(
                    digest,
                    "e"
                            + edge.getSUID()
                            + ":"
                            + edge.getSource().getSUID()
                            + (edge.isDirected() ? ">" : "-")
                            + edge.getTarget().getSUID());
        }
        addValues(digest, "nodes", network.getDefaultNodeTable(), nodes);
        addValues(digest, "edges", network.getDefaultEdgeTable(), edges);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void addValues(
            MessageDigest digest,
            String section,
            CyTable table,
            List<? extends CyIdentifiable> rows) {
        add(digest, section);
        List<CyColumn> columns = new ArrayList<>(table.getColumns());
        columns.removeIf(column -> CyNetwork.SELECTED.equals(column.getName()));
        columns.sort(Comparator.comparing(CyColumn::getName));
        for (CyColumn column : columns) {
            add(digest, "c" + column.getName() + ":" + column.getType().getSimpleName());
            for (CyIdentifiable element : rows) {
                Object value = table.getRow(element.getSUID()).getRaw(column.getName());
                add(digest, Objects.toString(value, "\u0000"));
            }
        }
    }

    private static <T extends CyIdentifiable> List<T> sorted(List<T> elements) {
        List<T> sorted = new ArrayList<>(elements);
        sorted.sort(Comparator.comparing(CyIdentifiable::getSUID));
        return sorted;
    }

    /** Adds {@code text} with its length, so neighbouring values can't run together. */
    private static void add(MessageDigest digest, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java runtime provides SHA-256", e);
        }
    }
}
