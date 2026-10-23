package org.cytoscape.serviceapps.run;

import java.time.Duration;
import java.util.Properties;

import org.cytoscape.serviceapps.client.ClientSettings;

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

    public static RunSettings from(Properties properties) {
        if (properties == null) {
            return DEFAULTS;
        }
        return new RunSettings(
                ClientSettings.millis(properties, POLL_INTERVAL_MS, DEFAULTS.pollInterval()),
                ClientSettings.millis(properties, RUN_TIMEOUT_MS, DEFAULTS.timeout()));
    }
}
