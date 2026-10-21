package org.cytoscape.serviceapps.client;

import java.net.http.HttpTimeoutException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.model.ServiceStatus;
import org.cytoscape.serviceapps.model.TaskRequest;
import org.cytoscape.serviceapps.model.TaskStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceAppClientTest {

    private static final String BASE = "https://example.org/cy/hello";

    private final FakeTransport transport = new FakeTransport();
    private final ServiceAppClient client = new ServiceAppClient(BASE + "/", transport);

    @Test
    void getMetadataCallsTheBaseUrl() {
        transport.reply(200, "{\"name\":\"Hello\",\"version\":\"1.0\"}");

        ServiceMetadata meta = client.getMetadata();

        assertEquals("Hello", meta.name);
        assertEquals("GET", transport.last().method());
        assertEquals(BASE, transport.last().uri().toString());
        assertNull(transport.last().body());
    }

    @Test
    void getStatusCallsStatusAndKeepsTheHttpStatus() {
        transport.reply(200, "{\"status\":\"ok\",\"version\":\"1.0\",\"numberOfRunningTasks\":2}");

        ServiceStatus status = client.getStatus();

        assertEquals("ok", status.status);
        assertEquals(2, status.numberOfRunningTasks);
        assertEquals(200, status.httpStatus);
        assertEquals(BASE + "/status", transport.last().uri().toString());
    }

    @Test
    void submitPostsTheRequestAndReturnsTheId() {
        transport.reply(202, "{\"id\":\"261fb9b7\"}");

        String id =
                client.submit(
                        new TaskRequest(
                                "Hello",
                                JsonParser.parseString("{\"x\":1}"),
                                Map.of("Greeting", "hi")));

        assertEquals("261fb9b7", id);
        assertEquals("POST", transport.last().method());
        assertEquals(BASE, transport.last().uri().toString());
        assertEquals(
                "{\"algorithm\":\"Hello\",\"data\":{\"x\":1},\"parameters\":{\"Greeting\":\"hi\"}}",
                transport.last().body());
    }

    @Test
    void submitWithoutAnIdFails() {
        transport.reply(202, "{}");

        ServiceAppException e =
                assertThrows(ServiceAppException.class, () -> client.submit(new TaskRequest()));
        assertTrue(e.getMessage().contains("no task id"), e.getMessage());
    }

    @Test
    void getTaskStatusAndResultUseTheTaskUrls() {
        transport.reply(200, "{\"status\":\"processing\",\"progress\":40,\"result\":null}");
        transport.reply(
                200,
                "{\"id\":\"t 1\",\"status\":\"complete\",\"progress\":100,"
                        + "\"result\":[{\"action\":\"openURL\",\"data\":{\"url\":\"u\"}}]}");

        TaskStatus status = client.getTaskStatus("t 1");
        assertEquals(40.0, status.progress);
        assertEquals(BASE + "/t%201/status", transport.last().uri().toString());

        TaskStatus result = client.getTaskResult("t 1");
        assertEquals("openURL", result.actions().get(0).action);
        assertEquals(BASE + "/t%201", transport.last().uri().toString());
    }

    @Test
    void deleteTaskSendsDelete() {
        transport.reply(204, "");

        client.deleteTask("t1");

        assertEquals("DELETE", transport.last().method());
        assertEquals(BASE + "/t1", transport.last().uri().toString());
    }

    @Test
    void aNon2xxReplyCarriesTheServiceMessage() {
        transport.reply(500, "{\"message\":\"algorithm crashed\"}");

        ServiceAppException e = assertThrows(ServiceAppException.class, client::getMetadata);

        assertEquals(500, e.httpStatus());
        assertTrue(e.getMessage().contains("HTTP 500"), e.getMessage());
        assertTrue(e.getMessage().contains("algorithm crashed"), e.getMessage());
    }

    @Test
    void aNon2xxTextReplyIsQuoted() {
        transport.reply(404, "Not Found");

        ServiceAppException e = assertThrows(ServiceAppException.class, client::getMetadata);

        assertEquals(404, e.httpStatus());
        assertTrue(e.getMessage().endsWith(": Not Found"), e.getMessage());
    }

    @Test
    void noResponseIsReportedAsUnreachable() {
        transport.fail(new HttpTimeoutException("request timed out"));

        ServiceAppException e = assertThrows(ServiceAppException.class, client::getStatus);

        assertEquals(ServiceAppException.NO_RESPONSE, e.httpStatus());
        assertTrue(e.getMessage().contains("could not reach"), e.getMessage());
        assertTrue(e.getMessage().contains("timed out"), e.getMessage());
    }

    @Test
    void invalidJsonIsReported() {
        transport.reply(200, "<html>oops</html>");

        ServiceAppException e = assertThrows(ServiceAppException.class, client::getMetadata);

        assertTrue(e.getMessage().contains("invalid metadata"), e.getMessage());
    }

    @Test
    void onlyHttpUrlsAreAccepted() {
        for (String url : new String[] {"", "ftp://example.org/x", "not a url", "file:///tmp/x"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ServiceAppClient(url, transport),
                    url);
        }
        assertEquals(
                "http://localhost:8765/hello",
                new ServiceAppClient("http://localhost:8765/hello//", transport).baseUrl());
    }

    @Test
    void aTaskIdIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> client.getTaskStatus(" "));
    }
}
