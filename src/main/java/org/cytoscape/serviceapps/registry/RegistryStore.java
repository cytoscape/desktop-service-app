package org.cytoscape.serviceapps.registry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import org.cytoscape.serviceapps.model.Json;
import org.cytoscape.serviceapps.model.ServiceMetadata;

/**
 * Reads and writes {@code service-apps.json}. Package-private: only {@link ServiceAppRegistry}
 * touches the file.
 *
 * <p>Writes go to a temporary file in the same directory and are moved into place, so a crash
 * mid-write never leaves a half-written registry.
 */
class RegistryStore {

    static final String FILE_NAME = "service-apps.json";
    private static final int FORMAT_VERSION = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryStore.class);

    private final Path file;

    RegistryStore(Path file) {
        this.file = file;
    }

    /** One stored app. Timestamps are epoch milliseconds, so Gson needs no adapters. */
    static class Entry {
        String name;
        String version;
        String url;
        ServiceMetadata metadata;
        long installedAt;
        long lastRefreshedAt;
        boolean reachable = true;
        JsonObject lastValues = new JsonObject();

        InstalledServiceApp snapshot() {
            return new InstalledServiceApp(
                    name,
                    version,
                    url,
                    Instant.ofEpochMilli(installedAt),
                    Instant.ofEpochMilli(lastRefreshedAt),
                    reachable);
        }
    }

    private static class Document {
        int formatVersion = FORMAT_VERSION;
        List<Entry> apps = new ArrayList<>();
    }

    /**
     * Loads the stored apps, keyed by name. A missing file is an empty registry. An unreadable or
     * corrupt file is moved aside with a warning, so a bad file never stops Desktop from starting.
     */
    Map<String, Entry> load() {
        Map<String, Entry> apps = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return apps;
        }
        try {
            Document document =
                    Json.parse(Files.readString(file, StandardCharsets.UTF_8), Document.class);
            if (document.apps != null) {
                for (Entry entry : document.apps) {
                    if (entry == null || entry.name == null || entry.name.isBlank()) {
                        continue;
                    }
                    if (entry.lastValues == null) {
                        entry.lastValues = new JsonObject();
                    }
                    apps.put(entry.name, entry);
                }
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            Path aside = file.resolveSibling(FILE_NAME + ".corrupt-" + System.currentTimeMillis());
            LOGGER.warn(
                    "Could not read installed service apps from {} ({}); moved it to {} and"
                            + " starting with none installed",
                    file,
                    e.getMessage(),
                    aside);
            try {
                Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveFailure) {
                LOGGER.warn("Could not move {} aside: {}", file, moveFailure.getMessage());
            }
            apps.clear();
        }
        return apps;
    }

    void save(Map<String, Entry> apps) throws IOException {
        Document document = new Document();
        document.apps.addAll(apps.values());
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), FILE_NAME, ".tmp");
        try {
            Files.writeString(temp, Json.write(document), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temp,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
