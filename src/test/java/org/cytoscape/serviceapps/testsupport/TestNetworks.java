package org.cytoscape.serviceapps.testsupport;

import java.util.List;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.NetworkTestSupport;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.ScenarioCatalog;
import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;

/** Real networks for tests, from Cytoscape's model test support. */
public final class TestNetworks {

    private static final NetworkTestSupport SUPPORT = new NetworkTestSupport();

    /** The user columns {@link #withEveryColumnType()} adds, by CX2 type. */
    public static final List<String> TYPED_COLUMNS =
            List.of(
                    "c_string",
                    "c_long",
                    "c_integer",
                    "c_double",
                    "c_boolean",
                    "c_list_of_string",
                    "c_list_of_long",
                    "c_list_of_integer",
                    "c_list_of_double",
                    "c_list_of_boolean");

    private TestNetworks() {}

    public static NetworkTestSupport support() {
        return SUPPORT;
    }

    /** An empty network. */
    public static CyNetwork empty() {
        return SUPPORT.getNetwork();
    }

    /**
     * A network with two nodes, one edge, and one node and one edge column of every CX2 type. Node
     * columns are prefixed {@code c_}, edge columns {@code e_}, so a picker can never confuse them.
     */
    public static CyNetwork withEveryColumnType() {
        CyNetwork network = SUPPORT.getNetwork();
        CyNode a = network.addNode();
        CyNode b = network.addNode();
        network.addEdge(a, b, true);
        addTypedColumns(network.getDefaultNodeTable(), "c_");
        addTypedColumns(network.getDefaultEdgeTable(), "e_");
        network.getRow(a).set(CyNetwork.NAME, "alpha");
        network.getRow(b).set(CyNetwork.NAME, "beta");
        return network;
    }

    private static void addTypedColumns(CyTable table, String prefix) {
        table.createColumn(prefix + "string", String.class, false);
        table.createColumn(prefix + "long", Long.class, false);
        table.createColumn(prefix + "integer", Integer.class, false);
        table.createColumn(prefix + "double", Double.class, false);
        table.createColumn(prefix + "boolean", Boolean.class, false);
        table.createListColumn(prefix + "list_of_string", String.class, false);
        table.createListColumn(prefix + "list_of_long", Long.class, false);
        table.createListColumn(prefix + "list_of_integer", Integer.class, false);
        table.createListColumn(prefix + "list_of_double", Double.class, false);
        table.createListColumn(prefix + "list_of_boolean", Boolean.class, false);
    }

    /** A fixture scenario's metadata. */
    public static ServiceMetadata scenario(String name) {
        return Json.parse(ScenarioCatalog.get(name).metadata().toString(), ServiceMetadata.class);
    }

    /** Installs a fixture scenario, served at its URL. Returns the app's name. */
    public static String install(
            ServiceAppRegistry registry, FixtureServer fixture, String scenario) {
        ServiceMetadata meta = scenario(scenario);
        registry.install(meta.name, meta.version, fixture.url(scenario), meta);
        return meta.name;
    }
}
