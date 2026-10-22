package org.cytoscape.serviceapps.contract;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.NetworkTestSupport;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.serviceapps.client.JdkHttpTransport;
import org.cytoscape.serviceapps.client.ServiceAppClient;
import org.cytoscape.serviceapps.client.ServiceAppClientFactory;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.form.FormSpecBuilder;
import org.cytoscape.serviceapps.launch.ServiceAppLauncher;
import org.cytoscape.serviceapps.menu.ServiceAppLifecycle;
import org.cytoscape.serviceapps.menu.ServiceAppMenuTaskFactory;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.serviceapps.render.RenderServiceAppUI;
import org.cytoscape.serviceapps.request.IdMapper;
import org.cytoscape.serviceapps.request.RequestBuilder;
import org.cytoscape.serviceapps.run.ActiveRuns;
import org.cytoscape.serviceapps.run.RecordingTaskMonitor;
import org.cytoscape.serviceapps.run.RunSettings;
import org.cytoscape.serviceapps.run.ServiceAppRunFactory;
import org.cytoscape.serviceapps.testsupport.ContractNetwork;
import org.cytoscape.serviceapps.testsupport.FakeCx2Support;
import org.cytoscape.serviceapps.testsupport.ManualExecutor;
import org.cytoscape.serviceapps.testsupport.TestNetworks;
import org.cytoscape.util.swing.OpenBrowser;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.model.View;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.work.Task;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TunableValidator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reaches Desktop the way a user does, in the JVM: the menu entry that {@link ServiceAppLifecycle}
 * registered for the app is clicked, {@link ServiceAppLauncher} builds the dialog, the dialog is
 * submitted with its defaults, and the run applies the result to real networks and views. CX2 goes
 * through {@link FakeCx2Support}; the browser, Desktop's current network and its view manager are
 * recorded.
 */
public class JvmMenuHarness implements ContractHarness {

    private final FixtureServer fixture;
    private final Path dir;

    private final NetworkTestSupport networks = new NetworkTestSupport();
    private final CyNetworkManager networkManager = networks.getNetworkManager();
    private final Map<CyNetwork, List<CyNetworkView>> views = new HashMap<>();
    private final CyNetworkViewManager viewManager = mock(CyNetworkViewManager.class);
    private final CyApplicationManager applications = mock(CyApplicationManager.class);
    private final List<String> opened = new CopyOnWriteArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, ServiceAppMenuTaskFactory> entries = new HashMap<>();

    private CyNetwork current;
    private CyNetworkView currentView;
    private ContractNetwork target;
    private ContractNetwork other;
    private ServiceAppRegistry registry;
    private ServiceAppLifecycle lifecycle;
    private int resets;
    private volatile boolean browserFails;

    public JvmMenuHarness(FixtureServer fixture, Path dir) {
        this.fixture = fixture;
        this.dir = dir;
        when(viewManager.getNetworkViews(any(CyNetwork.class)))
                .thenAnswer(
                        call -> List.copyOf(views.getOrDefault(call.getArgument(0), List.of())));
        doAnswer(
                        call -> {
                            CyNetworkView view = call.getArgument(0);
                            views.computeIfAbsent(view.getModel(), n -> new ArrayList<>())
                                    .add(view);
                            return null;
                        })
                .when(viewManager)
                .addNetworkView(any(CyNetworkView.class));
        when(applications.getCurrentNetwork()).thenAnswer(call -> current);
        when(applications.getCurrentNetworkView()).thenAnswer(call -> currentView);
        doAnswer(
                        call -> {
                            current = call.getArgument(0);
                            List<CyNetworkView> of = views.getOrDefault(current, List.of());
                            currentView = of.isEmpty() ? null : of.get(0);
                            return null;
                        })
                .when(applications)
                .setCurrentNetwork(any());
    }

    @Override
    public void reset() {
        if (lifecycle != null) {
            lifecycle.shutdown();
        }
        new ArrayList<>(networkManager.getNetworkSet()).forEach(networkManager::destroyNetwork);
        views.clear();
        opened.clear();
        warnings.clear();
        entries.clear();

        target = new ContractNetwork("target");
        other = new ContractNetwork("other");
        for (ContractNetwork network : List.of(target, other)) {
            networkManager.addNetwork(network.network);
            views.put(network.network, new ArrayList<>(List.of(network.view)));
        }
        current = target.network;
        currentView = target.view;

        OpenBrowser browser = mock(OpenBrowser.class);
        browserFails = false;
        when(browser.openURL(anyString(), anyBoolean()))
                .thenAnswer(
                        call -> {
                            if (browserFails) {
                                return false;
                            }
                            opened.add(call.getArgument(0));
                            return true;
                        });
        FakeCx2Support cx2 =
                new FakeCx2Support(
                        networkManager,
                        viewManager,
                        ContractNetwork.views().getNetworkViewFactory());
        ServiceAppClientFactory clients =
                url ->
                        new ServiceAppClient(
                                url,
                                new JdkHttpTransport(
                                        Duration.ofSeconds(5), Duration.ofSeconds(10)));
        registry = ServiceAppRegistry.inDirectory(dir.resolve("registry-" + resets++));
        ActiveRuns active = new ActiveRuns();
        ServiceAppRunFactory runs =
                new ServiceAppRunFactory(
                        registry,
                        clients,
                        new RequestBuilder(cx2),
                        RenderServiceAppUI.standard(viewManager, browser, cx2, applications),
                        () -> new RunSettings(Duration.ofMillis(10), Duration.ofSeconds(30)),
                        active);
        ServiceAppLauncher launcher =
                new ServiceAppLauncher(
                        registry,
                        new FormSpecBuilder(),
                        mock(org.cytoscape.work.TaskManager.class),
                        applications,
                        runs,
                        cx2,
                        (title, message) -> warnings.add(message));
        CyServiceRegistrar registrar = mock(CyServiceRegistrar.class);
        doAnswer(
                        call -> {
                            ServiceAppMenuTaskFactory entry = call.getArgument(0);
                            entries.put(entry.appName(), entry);
                            return null;
                        })
                .when(registrar)
                .registerService(any(), eq(TaskFactory.class), any(Properties.class));
        lifecycle =
                new ServiceAppLifecycle(
                        registrar, registry, launcher, cx2, clients, active, new ManualExecutor());
        lifecycle.start();
    }

    @Override
    public void select(long... nodeIds) {
        for (long id : nodeIds) {
            target.network.getRow(target.node(id)).set(CyNetwork.SELECTED, true);
        }
    }

    @Override
    public void addTargetView() {
        views.get(target.network).add(target.newView());
    }

    @Override
    public void addNodeColumn(String name, Class<?> type) {
        target.network.getDefaultNodeTable().createColumn(name, type, false);
    }

    @Override
    public void setNetworkAttribute(String name, String value) {
        var table = target.network.getDefaultNetworkTable();
        if (table.getColumn(name) == null) {
            table.createColumn(name, String.class, false);
        }
        target.network.getRow(target.network).set(name, value);
    }

    @Override
    public boolean canFailBrowser() {
        return true;
    }

    @Override
    public void failBrowser() {
        browserFails = true;
    }

    @Override
    public Outcome run(String scenario, Ui ui) {
        ServiceMetadata meta = TestNetworks.scenario(scenario);
        if (registry.get(meta.name).isEmpty()) {
            registry.install(meta.name, meta.version, fixture.url(scenario), meta);
        }
        switch (ui) {
            case CURRENT_VIEW -> {
                current = target.network;
                currentView = views.get(target.network).get(0);
            }
            case NO_CURRENT_VIEW -> {
                current = target.network;
                currentView = null;
            }
            case NO_NETWORK -> {
                current = null;
                currentView = null;
            }
        }
        ServiceAppMenuTaskFactory entry = entries.get(meta.name);
        if (entry == null) {
            return new Outcome(false, "no menu entry was registered for " + meta.name);
        }
        if (!entry.isReady()) {
            return new Outcome(false, "the menu entry for " + meta.name + " is disabled");
        }

        warnings.clear();
        RecordingTaskMonitor monitor = new RecordingTaskMonitor();
        TaskIterator tasks = entry.createTaskIterator();
        try {
            while (tasks.hasNext()) {
                Task task = tasks.next();
                if (task instanceof TunableValidator dialog) {
                    StringBuilder problems = new StringBuilder();
                    if (dialog.getValidationState(problems)
                            != TunableValidator.ValidationState.OK) {
                        return new Outcome(false, "the dialog's defaults are invalid: " + problems);
                    }
                }
                task.run(monitor);
            }
        } catch (Exception e) {
            return new Outcome(false, e.getMessage());
        }
        if (!warnings.isEmpty()) {
            return new Outcome(false, String.join("; ", warnings));
        }
        return new Outcome(true, monitor.statusMessage());
    }

    // --- the target network ---

    private IdMapper ids() {
        return IdMapper.of(target.network);
    }

    @Override
    public long targetSuid() {
        return target.network.getSUID();
    }

    @Override
    public int nodeCount() {
        return target.network.getNodeCount();
    }

    @Override
    public int edgeCount() {
        return target.network.getEdgeCount();
    }

    @Override
    public boolean hasNode(long id) {
        CyNode node = ids().node(id);
        return node != null && target.network.containsNode(node);
    }

    @Override
    public boolean hasEdge(long id) {
        CyEdge edge = ids().edge(id);
        return edge != null && target.network.containsEdge(edge);
    }

    @Override
    public boolean nodeSelected(long id) {
        return Boolean.TRUE.equals(
                target.network.getRow(ids().node(id)).get(CyNetwork.SELECTED, Boolean.class));
    }

    @Override
    public boolean edgeSelected(long id) {
        return Boolean.TRUE.equals(
                target.network.getRow(ids().edge(id)).get(CyNetwork.SELECTED, Boolean.class));
    }

    @Override
    public double[] position(int view, long nodeId) {
        return position(views.get(target.network).get(view), ids().node(nodeId));
    }

    private static double[] position(CyNetworkView view, CyNode node) {
        View<CyNode> nodeView = view.getNodeView(node);
        return new double[] {
            nodeView.getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION),
            nodeView.getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION),
            nodeView.getVisualProperty(BasicVisualLexicon.NODE_Z_LOCATION)
        };
    }

    @Override
    public Object nodeValue(String column, long nodeId) {
        CyRow row = target.network.getRow(ids().node(nodeId));
        return row.getTable().getColumn(column) == null ? null : row.getRaw(column);
    }

    @Override
    public Class<?> nodeColumnType(String column) {
        var found = target.network.getDefaultNodeTable().getColumn(column);
        return found == null ? null : found.getType();
    }

    @Override
    public Object networkValue(String column) {
        return target.network.getRow(target.network).getRaw(column);
    }

    @Override
    public Map<String, Object> targetState() {
        return state(target.network);
    }

    @Override
    public Map<String, Object> otherState() {
        return state(other.network);
    }

    /** Everything observable, keyed by CX element ID rather than SUID. */
    private Map<String, Object> state(CyNetwork network) {
        IdMapper ids = IdMapper.of(network);
        Map<String, Object> state = new TreeMap<>();
        state.put("network", values(network.getRow(network)));
        for (CyNode node : network.getNodeList()) {
            String key = "node " + ids.id(node);
            state.put(key, values(network.getRow(node)));
            List<CyNetworkView> of = views.getOrDefault(network, List.of());
            for (int i = 0; i < of.size(); i++) {
                state.put(key + " view " + i, List.of(toList(position(of.get(i), node))));
            }
        }
        for (CyEdge edge : network.getEdgeList()) {
            state.put("edge " + ids.id(edge), values(network.getRow(edge)));
        }
        return state;
    }

    private static Map<String, Object> values(CyRow row) {
        Map<String, Object> values = new TreeMap<>(row.getAllValues());
        values.remove(CyIdentifiable.SUID);
        return values;
    }

    private static List<Double> toList(double[] values) {
        List<Double> list = new ArrayList<>();
        for (double value : values) {
            list.add(value);
        }
        return list;
    }

    // --- Desktop as a whole ---

    @Override
    public int collectionCount() {
        return (int)
                networkManager.getNetworkSet().stream()
                        .map(network -> ((CySubNetwork) network).getRootNetwork())
                        .distinct()
                        .count();
    }

    @Override
    public String currentNetworkName() {
        return current == null ? null : current.getRow(current).get(CyNetwork.NAME, String.class);
    }

    @Override
    public int currentNetworkNodeCount() {
        return current == null ? 0 : current.getNodeCount();
    }

    @Override
    public int currentNetworkEdgeCount() {
        return current == null ? 0 : current.getEdgeCount();
    }

    @Override
    public int currentNetworkViewCount() {
        return current == null ? 0 : views.getOrDefault(current, List.of()).size();
    }

    @Override
    public List<String> openedUrls() {
        return List.copyOf(opened);
    }

    @Override
    public void close() {
        if (lifecycle != null) {
            lifecycle.shutdown();
        }
    }
}
