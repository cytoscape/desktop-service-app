package org.cytoscape.serviceapps;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cytoscape.application.CyApplicationConfiguration;
import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.application.swing.CyAction;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.io.read.InputStreamTaskFactory;
import org.cytoscape.io.write.CyNetworkViewWriterFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.property.CyProperty;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.serviceapps.call.ServiceInputParser;
import org.cytoscape.serviceapps.call.UiTargetResolver;
import org.cytoscape.serviceapps.client.ClientSettings;
import org.cytoscape.serviceapps.client.DefaultServiceAppClientFactory;
import org.cytoscape.serviceapps.client.ServiceAppClientFactory;
import org.cytoscape.serviceapps.client.ServiceAppStatusChecker;
import org.cytoscape.serviceapps.command.CommandTaskFactory;
import org.cytoscape.serviceapps.command.Commands;
import org.cytoscape.serviceapps.command.DescribeCommandTask;
import org.cytoscape.serviceapps.command.InstallCommandTask;
import org.cytoscape.serviceapps.command.ListCommandTask;
import org.cytoscape.serviceapps.command.StatusCommandTask;
import org.cytoscape.serviceapps.command.TaskCallCommandTask;
import org.cytoscape.serviceapps.command.TaskStatusCommandTask;
import org.cytoscape.serviceapps.command.UninstallCommandTask;
import org.cytoscape.serviceapps.cx.CxSupportBridge;
import org.cytoscape.serviceapps.form.FormSpecBuilder;
import org.cytoscape.serviceapps.form.ServiceAppFormHandlerFactory;
import org.cytoscape.serviceapps.launch.ServiceAppLauncher;
import org.cytoscape.serviceapps.launch.SwingUserMessages;
import org.cytoscape.serviceapps.manager.Confirmer;
import org.cytoscape.serviceapps.manager.ManageServiceAppsAction;
import org.cytoscape.serviceapps.manager.ServiceAppManagerDialog;
import org.cytoscape.serviceapps.manager.ServiceAppManagerModel;
import org.cytoscape.serviceapps.menu.ServiceAppLifecycle;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.serviceapps.render.RenderServiceAppUI;
import org.cytoscape.serviceapps.request.RequestBuilder;
import org.cytoscape.serviceapps.run.ActiveRuns;
import org.cytoscape.serviceapps.run.RunSettings;
import org.cytoscape.serviceapps.run.ServiceAppRunFactory;
import org.cytoscape.serviceapps.run.ServiceAppRunRegistry;
import org.cytoscape.util.swing.OpenBrowser;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.Task;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.swing.DialogTaskManager;
import org.cytoscape.work.swing.GUITunableHandlerFactory;

import static org.cytoscape.work.ServiceProperties.COMMAND;
import static org.cytoscape.work.ServiceProperties.COMMAND_DESCRIPTION;
import static org.cytoscape.work.ServiceProperties.COMMAND_EXAMPLE_JSON;
import static org.cytoscape.work.ServiceProperties.COMMAND_LONG_DESCRIPTION;
import static org.cytoscape.work.ServiceProperties.COMMAND_NAMESPACE;
import static org.cytoscape.work.ServiceProperties.COMMAND_SUPPORTS_JSON;
import static org.cytoscape.work.ServiceProperties.IN_MENU_BAR;

/**
 * OSGi entry point. Creates the one {@link ServiceAppRegistry} and hands it to everything that
 * needs installed-app state, publishes the {@code serviceapps} commands, and wires the Desktop UI:
 * menu entries, the input dialog, runs and the Service Apps manager.
 */
public class CyActivator extends AbstractCyActivator {

    public static final String COMMAND_NAMESPACE_VALUE = Commands.NAMESPACE;

    private static final Logger LOGGER = LoggerFactory.getLogger(CyActivator.class);

    private ServiceAppLifecycle lifecycle;
    private ExecutorService background;
    private ServiceAppRunRegistry runRegistry;

    @Override
    public void start(BundleContext bc) {
        CyApplicationConfiguration configuration = getService(bc, CyApplicationConfiguration.class);
        File directory = configuration.getAppConfigurationDirectoryLocation(CyActivator.class);
        ServiceAppRegistry registry = ServiceAppRegistry.inDirectory(directory.toPath());

        ServiceAppsProperties properties = new ServiceAppsProperties();
        Properties cyPropertyProps = new Properties();
        cyPropertyProps.setProperty("cyPropertyName", ServiceAppsProperties.FILE_NAME);
        registerService(bc, properties, CyProperty.class, cyPropertyProps);

        ServiceAppClientFactory clients =
                new DefaultServiceAppClientFactory(
                        () -> ClientSettings.from(properties.getProperties()));

        registerUiAndCommands(bc, registry, clients, properties);

        LOGGER.info(
                "Desktop Service App started with {} service app(s) installed",
                registry.list().size());
    }

    /**
     * Menu entries, the input dialog, the run engine, the Service Apps manager and the {@code
     * serviceapps} commands.
     *
     * <p>Each wiring step logs one INFO line; the Docker integration tests read them.
     */
    private void registerUiAndCommands(
            BundleContext bc,
            ServiceAppRegistry registry,
            ServiceAppClientFactory clients,
            ServiceAppsProperties properties) {
        CyServiceRegistrar registrar = getService(bc, CyServiceRegistrar.class);
        CyApplicationManager applications = getService(bc, CyApplicationManager.class);
        CyNetworkViewManager viewManager = getService(bc, CyNetworkViewManager.class);
        CySwingApplication swing = getService(bc, CySwingApplication.class);

        CyNetworkManager networkManager = getService(bc, CyNetworkManager.class);
        CxSupportBridge cx2 = new CxSupportBridge(networkManager, viewManager);
        registerServiceListener(
                bc,
                cx2::addWriterFactory,
                cx2::removeWriterFactory,
                CyNetworkViewWriterFactory.class);
        registerServiceListener(
                bc, cx2::addReaderFactory, cx2::removeReaderFactory, InputStreamTaskFactory.class);

        registerService(
                bc,
                new ServiceAppFormHandlerFactory(),
                GUITunableHandlerFactory.class,
                new Properties());
        LOGGER.info("Service app form handler registered");

        ActiveRuns active = new ActiveRuns();
        ServiceAppRunFactory runs =
                new ServiceAppRunFactory(
                        registry,
                        clients,
                        new RequestBuilder(cx2),
                        RenderServiceAppUI.standard(
                                viewManager, getService(bc, OpenBrowser.class), cx2, applications),
                        () -> RunSettings.from(properties.getProperties()),
                        active);
        ServiceAppLauncher launcher =
                new ServiceAppLauncher(
                        registry,
                        new FormSpecBuilder(),
                        getService(bc, DialogTaskManager.class),
                        applications,
                        runs,
                        cx2,
                        new SwingUserMessages(swing::getJFrame));

        background = Executors.newFixedThreadPool(2, daemonThreads());
        lifecycle =
                new ServiceAppLifecycle(
                        registrar, registry, launcher, cx2, clients, active, background);
        lifecycle.start();

        ServiceAppStatusChecker checker = new ServiceAppStatusChecker(clients);
        ManageServiceAppsAction manage =
                new ManageServiceAppsAction(
                        () ->
                                new ServiceAppManagerDialog(
                                        swing.getJFrame(),
                                        new ServiceAppManagerModel(
                                                registry, launcher, checker, background),
                                        Confirmer.OPTION_PANE));
        registerService(bc, manage, CyAction.class, new Properties());
        LOGGER.info("Manage Service Apps… registered under Tools > Service Apps");

        runRegistry = new ServiceAppRunRegistry(active);
        UiTargetResolver targets = new UiTargetResolver(networkManager, viewManager, applications);
        ServiceInputParser inputs = new ServiceInputParser(new FormSpecBuilder());
        Map<Class<? extends Task>, Supplier<Task>> commands = new LinkedHashMap<>();
        commands.put(ListCommandTask.class, () -> new ListCommandTask(registry));
        commands.put(InstallCommandTask.class, () -> new InstallCommandTask(registry, clients));
        commands.put(UninstallCommandTask.class, () -> new UninstallCommandTask(registry));
        commands.put(StatusCommandTask.class, () -> new StatusCommandTask(registry, clients));
        commands.put(DescribeCommandTask.class, () -> new DescribeCommandTask(registry, cx2));
        commands.put(
                TaskCallCommandTask.class,
                () -> new TaskCallCommandTask(registry, targets, inputs, runs, runRegistry, cx2));
        commands.put(TaskStatusCommandTask.class, () -> new TaskStatusCommandTask(runRegistry));
        for (Class<? extends Task> type : Commands.ALL) {
            Supplier<Task> tasks = commands.get(type);
            if (tasks == null) {
                throw new IllegalStateException("no task supplier for " + type.getSimpleName());
            }
            registerCommand(bc, Commands.Command.of(type), tasks);
        }
    }

    /** Cancels runs in progress and removes every menu entry before the bundle stops. */
    @Override
    public void shutDown() {
        if (lifecycle != null) {
            lifecycle.shutdown();
        }
        if (runRegistry != null) {
            runRegistry.shutdown();
        }
        if (background != null) {
            background.shutdownNow();
        }
        super.shutDown();
    }

    private static ThreadFactory daemonThreads() {
        AtomicInteger count = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "service-apps-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Publishes one command to the Command Line, CyREST and automation clients.
     *
     * <p>{@code IN_MENU_BAR=false} is required: the menu-bar builder listens to every {@code
     * TaskFactory} and falls back to the Apps menu, so a command registered without it also adds a
     * menu item.
     */
    private void registerCommand(BundleContext bc, Commands.Command command, Supplier<Task> tasks) {
        Properties props = new Properties();
        props.setProperty(IN_MENU_BAR, "false");
        props.setProperty(COMMAND_NAMESPACE, COMMAND_NAMESPACE_VALUE);
        props.setProperty(COMMAND, command.name());
        props.setProperty(COMMAND_DESCRIPTION, command.description());
        props.setProperty(COMMAND_LONG_DESCRIPTION, command.longDescription());
        props.setProperty(COMMAND_SUPPORTS_JSON, "true");
        props.setProperty(COMMAND_EXAMPLE_JSON, command.exampleJson());
        registerService(bc, new CommandTaskFactory(tasks), TaskFactory.class, props);
    }
}
