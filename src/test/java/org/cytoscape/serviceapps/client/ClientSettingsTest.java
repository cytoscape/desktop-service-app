package org.cytoscape.serviceapps.client;

import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientSettingsTest {

    @Test
    void defaultsAreTenAndThirtySeconds() {
        assertEquals(Duration.ofSeconds(10), ClientSettings.DEFAULTS.connectTimeout());
        assertEquals(Duration.ofSeconds(30), ClientSettings.DEFAULTS.readTimeout());
        assertEquals(ClientSettings.DEFAULTS, ClientSettings.from(null));
        assertEquals(ClientSettings.DEFAULTS, ClientSettings.from(new Properties()));
    }

    @Test
    void readsMilliseconds() {
        Properties properties = new Properties();
        properties.setProperty(ClientSettings.CONNECT_TIMEOUT_MS, "2500");
        properties.setProperty(ClientSettings.READ_TIMEOUT_MS, " 60000 ");

        ClientSettings settings = ClientSettings.from(properties);

        assertEquals(Duration.ofMillis(2500), settings.connectTimeout());
        assertEquals(Duration.ofMinutes(1), settings.readTimeout());
    }

    @Test
    void invalidValuesFallBackToDefaults() {
        Properties properties = new Properties();
        properties.setProperty(ClientSettings.CONNECT_TIMEOUT_MS, "soon");
        properties.setProperty(ClientSettings.READ_TIMEOUT_MS, "-5");

        assertEquals(ClientSettings.DEFAULTS, ClientSettings.from(properties));
    }
}
