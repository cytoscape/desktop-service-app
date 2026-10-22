package org.cytoscape.serviceapps.client;

import java.io.IOException;
import java.net.URI;

/**
 * Sends one HTTP request. The seam that lets tests replace the network.
 *
 * <p>Implementations apply their own connect and read timeouts.
 */
public interface HttpTransport {

    /**
     * @param method {@code GET}, {@code POST} or {@code DELETE}
     * @param jsonBody the request body, sent as {@code application/json}; null for none
     * @throws IOException if no response was received, including on timeout
     */
    Response send(String method, URI uri, String jsonBody) throws IOException;

    /** The status code and body of a response. */
    record Response(int status, String body) {
        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }
}
