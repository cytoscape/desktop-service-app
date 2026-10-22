package org.cytoscape.serviceapps.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** {@link HttpTransport} over the JDK's {@code java.net.http} client. */
public class JdkHttpTransport implements HttpTransport {

    private final HttpClient client;
    private final Duration readTimeout;

    public JdkHttpTransport(Duration connectTimeout, Duration readTimeout) {
        this.client =
                HttpClient.newBuilder()
                        .connectTimeout(connectTimeout)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
        this.readTimeout = readTimeout;
    }

    @Override
    public Response send(String method, URI uri, String jsonBody) throws IOException {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri)
                        .timeout(readTimeout)
                        .header("Accept", "application/json");
        if (jsonBody == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
        }
        try {
            HttpResponse<String> response =
                    client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while calling " + uri, e);
        }
    }
}
