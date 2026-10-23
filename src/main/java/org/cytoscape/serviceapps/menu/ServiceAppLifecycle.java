package org.cytoscape.serviceapps.menu;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.serviceapps.app.RunPrecondition;
import org.cytoscape.serviceapps.client.ServiceAppClientFactory;
import org.cytoscape.serviceapps.client.ServiceAppException;
import org.cytoscape.serviceapps.cx.Cx2Support;
import org.cytoscape.serviceapps.launch.ServiceAppLauncher;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.placement.MenuPlacement;
import org.cytoscape.serviceapps.placement.MenuPlacementResolver;
import org.cytoscape.serviceapps.registry.InstalledServiceApp;
import org.cytoscape.serviceapps.registry.RegistryEvent;
import org.cytoscape.serviceapps.registry.RegistryListener;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.serviceapps.run.ActiveRuns;
import org.cytoscape.work.TaskFactory;

import static org.cytoscape.work.ServiceProperties.ENABLE_FOR;
import static org.cytoscape.work.ServiceProperties.IN_TOOL_BAR;
import static org.cytoscape.work.ServiceProperties.MENU_GRAVITY;
import static org.cytoscape.work.ServiceProperties.PREFERRED_MENU;
import static org.cytoscape.work.ServiceProperties.TITLE;
import static org.cytoscape.work.ServiceProperties.TOOLTIP;

/**
 * Keeps Desktop's menus in step with the installed service apps.
 *
 * <ul>
 *   <li>At start, registers an entry for each app from its saved metadata, so menus appear even
 *       offline, then refreshes every app's metadata in the background.
 *   <li>On install, upgrade, new metadata or a reachability change, re-registers the app's entry if
 *       anything about it changed.
 *   <li>On uninstall, removes the entry and cancels the app's runs.
 *   <li>When CX Support appears or goes away, re-checks every entry.
 * </ul>
 *
 * Every menu entry change is logged, so the wiring can be checked from Desktop's log.
 */
public class ServiceAppLifecycle implements RegistryListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceAppLifecycle.class);

    private record Entry(ServiceAppMenuTaskFactory factory, MenuPlacement placement, String key) {}

    private final CyServiceRegistrar registrar;
    private final EmptySubmenus submenus;
    private final ServiceAppRegistry registry;
    private final ServiceAppLauncher launcher;
    private final Cx2Support cx2;
    private final ServiceAppClientFactory clients;
    private final ActiveRuns active;
    private final ExecutorService refresher;

    private final Map<String, Entry> entries = new HashMap<>();
    private final Map<String, String> noEntry = new HashMap<>();
    private volatile boolean stopped;

    public ServiceAppLifecycle(
            CyServiceRegistrar registrar,
            ServiceAppRegistry registry,
            ServiceAppLauncher launcher,
            Cx2Support cx2,
            ServiceAppClientFactory clients,
            ActiveRuns active,
            ExecutorService refresher,
            EmptySubmenus submenus) {
        this.registrar = registrar;
        this.submenus = submenus;
        this.registry = registry;
        this.launcher = launcher;
        this.cx2 = cx2;
        this.clients = clients;
        this.active = active;
        this.refresher = refresher;
    }

    /** Registers entries from saved metadata, then refreshes every app in the background. */
    public void start() {
        registry.addListener(this);
        cx2.addAvailabilityListener(this::syncAll);
        syncAll();
        refreshAll();
    }

    /** Removes every entry, stops refreshing and cancels every run. */
    public void shutdown() {
        stopped = true;
        registry.removeListener(this);
        refresher.shutdownNow();
        synchronized (this) {
            new ArrayList<>(entries.keySet()).forEach(this::remove);
            noEntry.clear();
        }
        active.cancelAll();
    }

    @Override
    public void registryChanged(RegistryEvent event) {
        if (stopped) {
            return;
        }
        if (event.type() == RegistryEvent.Type.REMOVED) {
            synchronized (this) {
                remove(event.name());
                noEntry.remove(event.name());
            }
            active.cancelApp(event.name());
        } else {
            sync(event.name());
        }
    }

    /** Fetches every app's metadata again, in the background. */
    public void refreshAll() {
        for (InstalledServiceApp app : registry.list()) {
            try {
                refresher.execute(() -> refresh(app));
            } catch (RejectedExecutionException e) {
                return; // shutting down
            }
        }
    }

    void refresh(InstalledServiceApp app) {
        if (stopped) {
            return;
        }
        ServiceMetadata meta;
        try {
            meta = clients.forUrl(app.url()).getMetadata();
        } catch (ServiceAppException | IllegalArgumentException e) {
            registry.setReachable(app.name(), false);
            LOGGER.info(
                    "Refreshed metadata for '{}' (unreachable: {})", app.name(), e.getMessage());
            return;
        }
        if (!app.name().equals(meta.name)) {
            registry.setReachable(app.name(), false);
            LOGGER.warn(
                    "Refreshed metadata for '{}' (unreachable: {} now serves '{}')",
                    app.name(),
                    app.url(),
                    meta.name);
            return;
        }
        boolean upgraded = !Objects.equals(app.version(), meta.version);
        // A new version also forgets the dialog values remembered for the old one.
        registry.updateMeta(app.name(), meta.version, meta);
        LOGGER.info(
                "Refreshed metadata for '{}' (reachable, version {}{})",
                app.name(),
                meta.version,
                upgraded ? ", upgraded from " + app.version() : "");
    }

    private void syncAll() {
        if (stopped) {
            return;
        }
        for (InstalledServiceApp app : registry.list()) {
            sync(app.name());
        }
    }

    /**
     * Synchronized so the entry map and the registered services stay in step. Registering under the
     * lock is safe: Desktop's menu populator hands the change to the EDT without waiting.
     */
    private synchronized void sync(String name) {
        if (stopped) {
            return;
        }
        Optional<InstalledServiceApp> app = registry.get(name);
        Optional<ServiceMetadata> meta = registry.getMeta(name);
        if (app.isEmpty() || meta.isEmpty()) {
            remove(name);
            return;
        }
        Optional<MenuPlacement> placement = MenuPlacementResolver.resolve(meta.get());
        if (placement.isEmpty()) {
            remove(name);
            String reason = MenuPlacementResolver.whyNoPlacement(meta.get()).orElse("no menu");
            if (!reason.equals(noEntry.put(name, reason))) {
                LOGGER.info("No menu entry for '{}': {}", name, reason);
            }
            return;
        }
        noEntry.remove(name);

        Optional<String> disabled = disabledReason(app.get(), meta.get());
        Properties properties = properties(app.get(), meta.get(), placement.get(), disabled);
        String key = new TreeMap<>(properties).toString();
        Entry current = entries.get(name);
        if (current != null && current.key().equals(key)) {
            return;
        }
        unregister(name);
        ServiceAppMenuTaskFactory factory =
                new ServiceAppMenuTaskFactory(name, disabled.isEmpty(), launcher);
        registrar.registerService(factory, TaskFactory.class, properties);
        entries.put(name, new Entry(factory, placement.get(), key));
        LOGGER.info(
                "Menu entry registered: {} for '{}' ({})",
                placement.get().describe(),
                name,
                disabled.map(reason -> "disabled: " + reason).orElse("enabled"));
    }

    private Optional<String> disabledReason(InstalledServiceApp app, ServiceMetadata meta) {
        return RunPrecondition.unavailable(app, meta, cx2.available());
    }

    /** The menu entry's service properties: Desktop builds the item from these. */
    static Properties properties(
            InstalledServiceApp app,
            ServiceMetadata meta,
            MenuPlacement placement,
            Optional<String> disabled) {
        Properties properties = new Properties();
        properties.setProperty(PREFERRED_MENU, placement.preferredMenu());
        properties.setProperty(TITLE, placement.title());
        properties.setProperty(MENU_GRAVITY, Double.toString(placement.gravity()));
        properties.setProperty(IN_TOOL_BAR, "false");
        String tooltip = app.name() + " " + app.version() + " from " + host(app.url());
        if (disabled.isPresent()) {
            tooltip += ". Unavailable: " + disabled.get();
        } else {
            properties.setProperty(ENABLE_FOR, RunPrecondition.enableFor(meta));
        }
        properties.setProperty(TOOLTIP, tooltip);
        return properties;
    }

    private static String host(String url) {
        try {
            String authority = URI.create(url).getAuthority();
            return authority == null ? url : authority;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private void remove(String name) {
        if (unregister(name)) {
            LOGGER.info("Menu entry removed for '{}'", name);
        }
    }

    private boolean unregister(String name) {
        Entry entry = entries.remove(name);
        if (entry == null) {
            return false;
        }
        registrar.unregisterService(entry.factory(), TaskFactory.class);
        // Desktop leaves the entry's emptied submenus behind.
        submenus.purge(name, entry.placement());
        return true;
    }

    /** Apps that get no menu entry, for tests. */
    synchronized List<String> withoutEntry() {
        return new ArrayList<>(noEntry.keySet());
    }
}
