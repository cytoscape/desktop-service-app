package org.cytoscape.serviceapps.registry;

import java.time.Instant;

/**
 * A snapshot of one installed service app, as {@link ServiceAppRegistry} returns it.
 *
 * <p>Snapshots never change. The metadata and remembered values are read through the registry
 * ({@link ServiceAppRegistry#getMeta}, {@link ServiceAppRegistry#getLastValues}), which hands out
 * fresh copies.
 *
 * @param lastRefreshedAt when the metadata was last fetched from the service
 */
public record InstalledServiceApp(
        String name,
        String version,
        String url,
        Instant installedAt,
        Instant lastRefreshedAt,
        boolean reachable) {}
