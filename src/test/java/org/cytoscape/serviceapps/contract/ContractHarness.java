package org.cytoscape.serviceapps.contract;

import java.util.List;
import java.util.Map;

/**
 * How {@link ResultActionContract} reaches Desktop: the menu path in the JVM ({@link
 * JvmMenuHarness}), and {@code serviceapps task call} against Cytoscape in Docker ({@link
 * DockerTaskCallHarness}). Both run the same scenarios and assertions.
 *
 * <p>Every run starts from {@link #reset()}: a target network with the fixture's element IDs (nodes
 * 0, 1, 2, 7, 15, 25 and 100; edges 3 from 0 to 1 and 6 from 7 to 15) and one view, current; and a
 * second open network that must never change. Elements are addressed by their CX element ID.
 */
public interface ContractHarness extends AutoCloseable {

    /** What the run sees as Desktop's current network and view. */
    enum Ui {
        /** The target network and its first view are current. */
        CURRENT_VIEW,
        /** The target network is current, with no current view. */
        NO_CURRENT_VIEW,
        /** No network is open or current. */
        NO_NETWORK
    }

    /**
     * How a run ended.
     *
     * @param message the result summary on success, or why it failed
     */
    record Outcome(boolean succeeded, String message) {}

    /** Fresh target and second network, the target current with one view. */
    void reset();

    /** Selects these target nodes. */
    void select(long... nodeIds);

    /** Adds a second view of the target network. */
    void addTargetView();

    /** Adds a node column to the target network. */
    void addNodeColumn(String name, Class<?> type);

    /** Sets a network table attribute of the target, creating a text column if needed. */
    void setNetworkAttribute(String name, String value);

    /** Whether the run can target one view; false when it always targets every view. */
    default boolean supportsViewTarget() {
        return true;
    }

    /** Whether {@link #failBrowser()} works in this harness. */
    default boolean canFailBrowser() {
        return false;
    }

    /** Makes the next attempt to open a web browser fail. */
    default void failBrowser() {
        throw new UnsupportedOperationException("this harness can't make the browser fail");
    }

    /** Installs the fixture scenario and runs it the way this harness reaches Desktop. */
    Outcome run(String scenario, Ui ui);

    // --- the target network ---

    long targetSuid();

    int nodeCount();

    int edgeCount();

    boolean hasNode(long id);

    boolean hasEdge(long id);

    boolean nodeSelected(long id);

    boolean edgeSelected(long id);

    /** {x, y, z} of a node in the target's view {@code view} (0 is the first). */
    double[] position(int view, long nodeId);

    Object nodeValue(String column, long nodeId);

    Class<?> nodeColumnType(String column);

    Object networkValue(String column);

    /** Every row value, selection and position: equal states mean nothing observable differs. */
    Map<String, Object> targetState();

    // --- Desktop as a whole ---

    /** A snapshot of the second network, which no scenario may change. */
    Map<String, Object> otherState();

    /** Network collections (root networks) open. */
    int collectionCount();

    String currentNetworkName();

    int currentNetworkNodeCount();

    int currentNetworkEdgeCount();

    int currentNetworkViewCount();

    /** URLs opened in the system browser, in order. */
    List<String> openedUrls();

    @Override
    default void close() {}
}
