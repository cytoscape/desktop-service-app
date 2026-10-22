package org.cytoscape.serviceapps.app;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.NetworkTestSupport;
import org.cytoscape.serviceapps.model.Parameter;
import org.cytoscape.serviceapps.model.ServiceInputDefinition;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.InstalledServiceApp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunPreconditionTest {

    private static final NetworkTestSupport NETWORKS = new NetworkTestSupport();

    private static ServiceMetadata meta(String type, String scope) {
        ServiceMetadata meta = new ServiceMetadata();
        meta.name = "Hello";
        if (type != null || scope != null) {
            meta.serviceInputDefinition = new ServiceInputDefinition();
            meta.serviceInputDefinition.type = type;
            meta.serviceInputDefinition.scope = scope;
            if ("network".equals(type)) {
                meta.serviceInputDefinition.inputNetwork =
                        new ServiceInputDefinition.InputNetwork();
                meta.serviceInputDefinition.inputNetwork.model = "network";
                meta.serviceInputDefinition.inputNetwork.format = "cx2";
            }
        }
        return meta;
    }

    private static InstalledServiceApp app(boolean reachable) {
        return new InstalledServiceApp(
                "Hello", "1.0", "https://x.example", Instant.EPOCH, Instant.EPOCH, reachable);
    }

    /** A network with one node and one edge; selects as asked. */
    private static CyNetwork network(boolean selectNode, boolean selectEdge) {
        CyNetwork network = NETWORKS.getNetwork();
        CyNode a = network.addNode();
        CyNode b = network.addNode();
        CyEdge edge = network.addEdge(a, b, true);
        network.getRow(a).set(CyNetwork.SELECTED, selectNode);
        network.getRow(edge).set(CyNetwork.SELECTED, selectEdge);
        return network;
    }

    static Stream<Arguments> enableFor() {
        return Stream.of(
                Arguments.of(null, null, "always"),
                Arguments.of("node", "dynamic", "network"),
                Arguments.of("edge", "all", "network"),
                Arguments.of("network", null, "network"),
                Arguments.of("node", "selected", "selectedNodes"),
                Arguments.of("edge", "selected", "selectedEdges"),
                Arguments.of("network", "selected", "selectedNodesOrEdges"),
                Arguments.of("NODE", " Selected ", "selectedNodes"));
    }

    @ParameterizedTest(name = "type [{0}] scope [{1}]")
    @MethodSource("enableFor")
    void enableForMatchesTheInputTypeAndScope(String type, String scope, String expected) {
        assertEquals(expected, RunPrecondition.enableFor(meta(type, scope)));
    }

    static Stream<Arguments> selections() {
        return Stream.of(
                Arguments.of("node", false, false, "Select at least one node"),
                Arguments.of("node", false, true, "Select at least one node"),
                Arguments.of("node", true, false, null),
                Arguments.of("edge", true, false, "Select at least one edge"),
                Arguments.of("edge", false, true, null),
                Arguments.of("network", false, false, "Select at least one node or edge"),
                Arguments.of("network", true, false, null),
                Arguments.of("network", false, true, null));
    }

    @ParameterizedTest(name = "{0} input, node selected {1}, edge selected {2}")
    @MethodSource("selections")
    void selectedScopeNeedsTheSelectionTheAppWorksOn(
            String type, boolean node, boolean edge, String message) {
        Optional<String> reason =
                RunPrecondition.check(app(true), meta(type, "selected"), network(node, edge), true);

        if (message == null) {
            assertEquals(Optional.empty(), reason);
        } else {
            assertTrue(reason.orElseThrow().startsWith(message), reason.get());
        }
    }

    @Test
    void otherScopesOnlyNeedAnOpenNetwork() {
        assertEquals(
                Optional.empty(),
                RunPrecondition.check(
                        app(true), meta("node", "dynamic"), network(false, false), true));
        assertTrue(
                RunPrecondition.check(app(true), meta("node", "all"), null, true)
                        .orElseThrow()
                        .contains("Open a network first"));
    }

    @Test
    void anAppWithNoInputNeedsNothing() {
        assertEquals(
                Optional.empty(), RunPrecondition.check(app(true), meta(null, null), null, true));
    }

    @Test
    void anUnreachableAppCannotRun() {
        assertTrue(
                RunPrecondition.check(app(false), meta(null, null), null, true)
                        .orElseThrow()
                        .contains("can't be reached"));
    }

    @Test
    void anUnsupportedAppCannotRun() {
        ServiceMetadata meta = meta(null, null);
        Parameter file = new Parameter();
        file.displayName = "Input";
        file.type = Parameter.FILE;
        meta.parameters = java.util.List.of(file);

        assertTrue(
                RunPrecondition.check(app(true), meta, null, true)
                        .orElseThrow()
                        .contains("parameter type 'file'"));
    }
}
