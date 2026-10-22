package org.cytoscape.serviceapps.client;

/** Makes a {@link ServiceAppClient} for a service app URL. Injected so tests can replace it. */
public interface ServiceAppClientFactory {

    /**
     * @throws IllegalArgumentException if {@code url} is not an http or https URL
     */
    ServiceAppClient forUrl(String url);
}
