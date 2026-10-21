package org.cytoscape.serviceapps.client;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Records requests and answers them from a queue of canned responses. */
public class FakeTransport implements HttpTransport {

    public record Sent(String method, URI uri, String body) {}

    private final Deque<Object> replies = new ArrayDeque<>();
    public final List<Sent> sent = new ArrayList<>();

    public FakeTransport reply(int status, String body) {
        replies.add(new Response(status, body));
        return this;
    }

    public FakeTransport fail(IOException error) {
        replies.add(error);
        return this;
    }

    @Override
    public Response send(String method, URI uri, String jsonBody) throws IOException {
        sent.add(new Sent(method, uri, jsonBody));
        Object next = replies.poll();
        if (next == null) {
            throw new IllegalStateException("no canned reply for " + method + " " + uri);
        }
        if (next instanceof IOException error) {
            throw error;
        }
        return (Response) next;
    }

    public Sent last() {
        return sent.get(sent.size() - 1);
    }
}
