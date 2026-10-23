package org.cytoscape.serviceapps.registry;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.model.ServiceMetadata;

/**
 * The installed service apps. The only way to read or change them.
 *
 * <p>Every component that needs installed-app state takes this class as a constructor dependency:
 * the {@code serviceapps} commands, the lifecycle manager, menus, the launcher, the run engine and
 * the Service Apps manager. Nothing else reads or writes {@code service-apps.json}.
 *
 * <p>Thread-safe. Every change is saved before it returns, then announced to listeners. Reads
 * return copies, so callers can't change stored state by accident.
 */
public class ServiceAppRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceAppRegistry.class);

    private final RegistryStore store;
    private final Map<String, RegistryStore.Entry> apps;
    private final List<RegistryListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * The registry's directory, the same for every version of the Desktop Service App.
     *
     * <p>Desktop's folder for an app's data has the app's version in its name ({@code
     * app-data/org.cytoscape.serviceapps-0.1.0}), so data kept there is left behind by an upgrade.
     * The registry is kept beside it instead, in a folder named by the bundle alone ({@code
     * app-data/org.cytoscape.serviceapps}). Desktop always adds a version to its own folders, so
     * this one is never Desktop's.
     *
     * @param versionedAppDirectory Desktop's folder for this version of the app, from {@code
     *     CyApplicationConfiguration.getAppConfigurationDirectoryLocation}
     * @param bundleName the app's bundle symbolic name
     */
    public static Path directoryFor(File versionedAppDirectory, String bundleName) {
        if (versionedAppDirectory == null || versionedAppDirectory.getParentFile() == null) {
            throw new IllegalStateException(
                    "Cytoscape gave no app data folder for "
                            + bundleName
                            + ": "
                            + versionedAppDirectory);
        }
        if (bundleName == null || bundleName.isBlank()) {
            throw new IllegalStateException("the app's bundle has no symbolic name");
        }
        return versionedAppDirectory.getParentFile().toPath().resolve(bundleName);
    }

    /** A registry stored in {@code service-apps.json} inside {@code directory}. */
    public static ServiceAppRegistry inDirectory(Path directory) {
        return new ServiceAppRegistry(
                new RegistryStore(directory.resolve(RegistryStore.FILE_NAME)));
    }

    ServiceAppRegistry(RegistryStore store) {
        this.store = store;
        this.apps = store.load();
    }

    /** All installed apps, sorted by name. */
    public synchronized List<InstalledServiceApp> list() {
        List<InstalledServiceApp> snapshots = new ArrayList<>();
        for (RegistryStore.Entry entry : apps.values()) {
            snapshots.add(entry.snapshot());
        }
        snapshots.sort(
                Comparator.comparing(InstalledServiceApp::name, String.CASE_INSENSITIVE_ORDER));
        return snapshots;
    }

    public synchronized Optional<InstalledServiceApp> get(String name) {
        RegistryStore.Entry entry = apps.get(name);
        return entry == null ? Optional.empty() : Optional.of(entry.snapshot());
    }

    /** A copy of the app's cached metadata. */
    public synchronized Optional<ServiceMetadata> getMeta(String name) {
        RegistryStore.Entry entry = apps.get(name);
        if (entry == null || entry.metadata == null) {
            return Optional.empty();
        }
        return Optional.of(copy(entry.metadata));
    }

    /**
     * Installs an app, or replaces it if an app with this name is installed. The remembered dialog
     * values are kept for the same version and forgotten for another, as in {@link #updateMeta}.
     */
    public void install(String name, String version, String url, ServiceMetadata meta) {
        requireName(name);
        RegistryEvent event;
        synchronized (this) {
            long now = System.currentTimeMillis();
            RegistryStore.Entry existing = apps.get(name);
            RegistryStore.Entry entry = new RegistryStore.Entry();
            entry.name = name;
            entry.version = version;
            entry.url = url;
            entry.metadata = copy(meta);
            entry.installedAt = now;
            entry.lastRefreshedAt = now;
            entry.reachable = true;
            entry.lastValues = existing == null ? new JsonObject() : valuesFor(existing, version);
            apps.put(name, entry);
            persistOrRestore(name, existing);
            event =
                    new RegistryEvent(
                            existing == null
                                    ? RegistryEvent.Type.ADDED
                                    : RegistryEvent.Type.UPDATED,
                            name);
        }
        fire(event);
    }

    /**
     * @return false if no app with this name is installed
     */
    public boolean uninstall(String name) {
        synchronized (this) {
            RegistryStore.Entry removed = apps.remove(name);
            if (removed == null) {
                return false;
            }
            persistOrRestore(name, removed);
        }
        fire(new RegistryEvent(RegistryEvent.Type.REMOVED, name));
        return true;
    }

    /**
     * Records freshly fetched metadata. The registry follows the live {@code version}, so a
     * same-endpoint upgrade is picked up. Marks the app reachable. A different version forgets the
     * remembered dialog values, which may not fit its parameters.
     *
     * @return false if no app with this name is installed
     */
    public boolean updateMeta(String name, String version, ServiceMetadata meta) {
        synchronized (this) {
            RegistryStore.Entry entry = apps.get(name);
            if (entry == null) {
                return false;
            }
            RegistryStore.Entry before = entry.copy();
            entry.lastValues = valuesFor(entry, version);
            entry.version = version;
            entry.metadata = copy(meta);
            entry.lastRefreshedAt = System.currentTimeMillis();
            entry.reachable = true;
            persistOrRestore(name, before);
        }
        fire(new RegistryEvent(RegistryEvent.Type.UPDATED, name));
        return true;
    }

    /**
     * @return false if no app with this name is installed
     */
    public boolean setReachable(String name, boolean reachable) {
        synchronized (this) {
            RegistryStore.Entry entry = apps.get(name);
            if (entry == null) {
                return false;
            }
            if (entry.reachable == reachable) {
                return true;
            }
            RegistryStore.Entry before = entry.copy();
            entry.reachable = reachable;
            persistOrRestore(name, before);
        }
        fire(new RegistryEvent(RegistryEvent.Type.UPDATED, name));
        return true;
    }

    /** A copy of the dialog values last used with this app; empty if none. */
    public synchronized JsonObject getLastValues(String name) {
        RegistryStore.Entry entry = apps.get(name);
        return entry == null ? new JsonObject() : entry.lastValues.deepCopy();
    }

    /**
     * @return false if no app with this name is installed
     */
    public boolean saveLastValues(String name, JsonObject values) {
        synchronized (this) {
            RegistryStore.Entry entry = apps.get(name);
            if (entry == null) {
                return false;
            }
            RegistryStore.Entry before = entry.copy();
            entry.lastValues = values == null ? new JsonObject() : values.deepCopy();
            persistOrRestore(name, before);
        }
        fire(new RegistryEvent(RegistryEvent.Type.UPDATED, name));
        return true;
    }

    /** The values to keep when {@code entry} becomes {@code version}. */
    private static JsonObject valuesFor(RegistryStore.Entry entry, String version) {
        return Objects.equals(entry.version, version) ? entry.lastValues : new JsonObject();
    }

    /**
     * The installed app.
     *
     * @throws IllegalArgumentException naming the app when none of that name is installed
     */
    public InstalledServiceApp require(String name) {
        return get(name)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "no service app named '" + name + "' is installed"));
    }

    /**
     * The installed app's metadata.
     *
     * @throws IllegalArgumentException naming the app when none of that name is installed
     */
    public ServiceMetadata requireMeta(String name) {
        require(name);
        return getMeta(name)
                .orElseThrow(() -> new IllegalStateException(name + " has no metadata yet"));
    }

    public void addListener(RegistryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(RegistryListener listener) {
        listeners.remove(listener);
    }

    /**
     * Saves every app. If the save fails, puts {@code name}'s entry back as it was ({@code before},
     * or absent when null) and rethrows, so memory never holds a change the file doesn't.
     */
    private void persistOrRestore(String name, RegistryStore.Entry before) {
        try {
            store.save(apps);
        } catch (IOException e) {
            if (before == null) {
                apps.remove(name);
            } else {
                apps.put(name, before);
            }
            throw new UncheckedIOException("could not save installed service apps", e);
        }
    }

    private void fire(RegistryEvent event) {
        for (RegistryListener listener : listeners) {
            try {
                listener.registryChanged(event);
            } catch (RuntimeException e) {
                LOGGER.warn("Registry listener failed on {}", event, e);
            }
        }
    }

    private static ServiceMetadata copy(ServiceMetadata meta) {
        return meta == null ? null : Json.GSON.fromJson(Json.tree(meta), ServiceMetadata.class);
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("service app name is required");
        }
    }
}
