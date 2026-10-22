package org.cytoscape.serviceapps.run;

import java.time.Duration;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * How often a run polls its task and how long it may take, from {@code serviceapps.props}.
 *
 * @param pollInterval time between task status checks ({@code poll.interval.ms}, default 500 ms)
 * @param timeout the longest a run may take ({@code run.timeout.ms}, default 30 minutes)
 */
public record RunSettings(Duration pollInterval, Duration timeout) {

    public static final String POLL_INTERVAL_MS = "poll.interval.ms";
    public static final String RUN_TIMEOUT_MS = "run.timeout.ms";

    public static final RunSettings DEFAULTS =
            new RunSettings(Duration.ofMillis(500), Duration.ofMinutes(30));

    /** Three status checks in a row that fail end the run. */
    public static final int MAX_FAILED_POLLS = 3;

    private static final Logger LOGGER = LoggerFactory.getLogger(RunSettings.class);

    public static RunSettings from(Properties properties) {
        if (properties == null) {
            return DEFAULTS;
        }
        return new RunSettings(
                millis(properties, POLL_INTERVAL_MS, DEFAULTS.pollInterval()),
                millis(properties, RUN_TIMEOUT_MS, DEFAULTS.timeout()));
    }

    private static Duration millis(Properties properties, String key, Duration fallback) {
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
