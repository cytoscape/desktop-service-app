package org.cytoscape.serviceapps.client;

import java.util.function.Supplier;

/** Builds clients over {@link JdkHttpTransport}, with the timeouts current at the time. */
public class DefaultServiceAppClientFactory implements ServiceAppClientFactory {

    private final Supplier<ClientSettings> settings;

    public DefaultServiceAppClientFactory(Supplier<ClientSettings> settings) {
        this.settings = settings;
    }

    @Override
    public ServiceAppClient forUrl(String url) {
        ClientSettings current = settings.get();
        return new ServiceAppClient(
                url, new JdkHttpTransport(current.connectTimeout(), current.readTimeout()));
    }
}
