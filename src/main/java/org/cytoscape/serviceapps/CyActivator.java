package org.cytoscape.serviceapps;

import java.io.File;
import java.util.Properties;
import java.util.function.Supplier;

import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cytoscape.application.CyApplicationConfiguration;
import org.cytoscape.property.CyProperty;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.serviceapps.client.ClientSettings;
import org.cytoscape.serviceapps.client.DefaultServiceAppClientFactory;
import org.cytoscape.serviceapps.client.ServiceAppClientFactory;
import org.cytoscape.serviceapps.command.CommandTaskFactory;
import org.cytoscape.serviceapps.command.InstallCommandTask;
import org.cytoscape.serviceapps.command.ListCommandTask;
import org.cytoscape.serviceapps.command.StatusCommandTask;
import org.cytoscape.serviceapps.command.UninstallCommandTask;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.work.Task;
import org.cytoscape.work.TaskFactory;

import static org.cytoscape.work.ServiceProperties.COMMAND;
import static org.cytoscape.work.ServiceProperties.COMMAND_DESCRIPTION;
import static org.cytoscape.work.ServiceProperties.COMMAND_EXAMPLE_JSON;
import static org.cytoscape.work.ServiceProperties.COMMAND_LONG_DESCRIPTION;
import static org.cytoscape.work.ServiceProperties.COMMAND_NAMESPACE;
import static org.cytoscape.work.ServiceProperties.COMMAND_SUPPORTS_JSON;
import static org.cytoscape.work.ServiceProperties.IN_MENU_BAR;

/**
 * OSGi entry point. Creates the one {@link ServiceAppRegistry} and hands it to everything that
 * needs installed-app state, then publishes the {@code serviceapps} commands.
 */
public class CyActivator extends AbstractCyActivator {

    public static final String COMMAND_NAMESPACE_VALUE = "serviceapps";

    private static final Logger LOGGER = LoggerFactory.getLogger(CyActivator.class);

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

        registerCommands(bc, registry, clients);

        LOGGER.info(
                "Desktop Service App started with {} service app(s) installed",
                registry.list().size());
    }

    private void registerCommands(
            BundleContext bc, ServiceAppRegistry registry, ServiceAppClientFactory clients) {
        registerCommand(
                bc,
                ListCommandTask.NAME,
                ListCommandTask.DESCRIPTION,
                ListCommandTask.LONG_DESCRIPTION,
                ListCommandTask.EXAMPLE_JSON,
                () -> new ListCommandTask(registry));
        registerCommand(
                bc,
                InstallCommandTask.NAME,
                InstallCommandTask.DESCRIPTION,
                InstallCommandTask.LONG_DESCRIPTION,
                InstallCommandTask.EXAMPLE_JSON,
                () -> new InstallCommandTask(registry, clients));
        registerCommand(
                bc,
                UninstallCommandTask.NAME,
                UninstallCommandTask.DESCRIPTION,
                UninstallCommandTask.LONG_DESCRIPTION,
                UninstallCommandTask.EXAMPLE_JSON,
                () -> new UninstallCommandTask(registry));
        registerCommand(
                bc,
                StatusCommandTask.NAME,
                StatusCommandTask.DESCRIPTION,
                StatusCommandTask.LONG_DESCRIPTION,
                StatusCommandTask.EXAMPLE_JSON,
                () -> new StatusCommandTask(registry, clients));
    }

    /**
     * Publishes one command to the Command Line, CyREST and automation clients.
     *
     * <p>{@code IN_MENU_BAR=false} is required: the menu-bar builder listens to every {@code
     * TaskFactory} and falls back to the Apps menu, so a command registered without it also adds a
     * menu item.
     */
    private void registerCommand(
            BundleContext bc,
            String name,
            String description,
            String longDescription,
            String exampleJson,
            Supplier<Task> tasks) {
        Properties props = new Properties();
        props.setProperty(IN_MENU_BAR, "false");
        props.setProperty(COMMAND_NAMESPACE, COMMAND_NAMESPACE_VALUE);
        props.setProperty(COMMAND, name);
        props.setProperty(COMMAND_DESCRIPTION, description);
        props.setProperty(COMMAND_LONG_DESCRIPTION, longDescription);
        props.setProperty(COMMAND_SUPPORTS_JSON, "true");
        props.setProperty(COMMAND_EXAMPLE_JSON, exampleJson);
        registerService(bc, new CommandTaskFactory(tasks), TaskFactory.class, props);
    }
}
