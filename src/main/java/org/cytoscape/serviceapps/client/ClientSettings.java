package org.cytoscape.serviceapps.client;

import java.time.Duration;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connect and read timeouts for service app calls, read from the {@code serviceapps} properties.
 *
 * <p>Read fresh for each client, so a change in Edit &gt; Preferences applies to the next call.
 */
public record ClientSettings(Duration connectTimeout, Duration readTimeout) {

    public static final String CONNECT_TIMEOUT_MS = "connect.timeout.ms";
    public static final String READ_TIMEOUT_MS = "read.timeout.ms";

    public static final ClientSettings DEFAULTS =
            new ClientSettings(Duration.ofSeconds(10), Duration.ofSeconds(30));

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSettings.class);

    /** Missing or invalid values fall back to {@link #DEFAULTS}. */
    public static ClientSettings from(Properties properties) {
        if (properties == null) {
            return DEFAULTS;
        }
        return new ClientSettings(
                millis(properties, CONNECT_TIMEOUT_MS, DEFAULTS.connectTimeout()),
                millis(properties, READ_TIMEOUT_MS, DEFAULTS.readTimeout()));
    }

    /**
     * A positive number of milliseconds from {@code key}, or {@code fallback} when it is missing or
     * invalid (logged). Also reads {@code RunSettings}.
     */
    public static Duration millis(Properties properties, String key, Duration fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            long ms = Long.parseLong(value.strip());
            if (ms > 0) {
                return Duration.ofMillis(ms);
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        LOGGER.warn("Ignoring {}={}: expected a positive number of milliseconds", key, value);
        return fallback;
    }
}
