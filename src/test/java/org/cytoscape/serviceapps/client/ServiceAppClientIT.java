package org.cytoscape.serviceapps.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.FixtureServerExtension;
import org.cytoscape.serviceapps.fixture.ScenarioCatalog;
import org.cytoscape.serviceapps.model.ResultAction;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.model.ServiceStatus;
import org.cytoscape.serviceapps.model.TaskRequest;
import org.cytoscape.serviceapps.model.TaskStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** All six endpoints over real HTTP, against the hello-world fixture. */
@ExtendWith(FixtureServerExtension.class)
class ServiceAppClientIT {

    private static ServiceAppClient client(FixtureServer fixture, String scenario) {
        return new ServiceAppClient(
                fixture.url(scenario),
                new JdkHttpTransport(Duration.ofSeconds(5), Duration.ofSeconds(10)));
    }

    private static TaskRequest request() {
        return new TaskRequest(
                "Hello World", JsonParser.parseString("{\"nodes\":[]}"), Map.of("Greeting", "hi"));
    }

    @Test
    void getMetadata(FixtureServer fixture) {
        ServiceMetadata meta = client(fixture, ScenarioCatalog.HELLO).getMetadata();

        assertEquals("Hello World", meta.name);
        assertEquals("1.0", meta.version);
        assertEquals("Greeting", meta.parameters().get(0).displayName);
    }

    @Test
    void getStatus(FixtureServer fixture) {
        ServiceStatus status = client(fixture, ScenarioCatalog.HELLO).getStatus();

        assertEquals("ok", status.status);
        assertEquals("1.0", status.version);
        assertEquals(200, status.httpStatus);
    }

    @Test
    void submitPollFetchAndDeleteATask(FixtureServer fixture) {
        ServiceAppClient client = client(fixture, ScenarioCatalog.HELLO);

        String id = client.submit(request());
        TaskStatus status;
        int polls = 0;
        do {
            status = client.getTaskStatus(id);
            assertTrue(status.result == null || status.result.isJsonNull());
            polls++;
        } while (status.progress < 100 && polls < 10);
        TaskStatus result = client.getTaskResult(id);
        client.deleteTask(id);

        assertTrue(status.isComplete());
        assertTrue(result.isComplete());
        List<ResultAction> actions = result.actions();
        assertEquals(ResultAction.UPDATE_LAYOUTS, actions.get(0).action);
        assertFalse(fixture.liveTaskIds().contains(id));

        FixtureServer.RecordedRequest submitted = fixture.requests().get(0);
        assertEquals("POST", submitted.method());
        JsonObject body = JsonParser.parseString(submitted.body()).getAsJsonObject();
        assertEquals("Hello World", body.get("algorithm").getAsString());
        assertEquals("hi", body.getAsJsonObject("parameters").get("Greeting").getAsString());

        ServiceAppException gone =
                assertThrows(ServiceAppException.class, () -> client.getTaskStatus(id));
        assertEquals(404, gone.httpStatus());
    }

    @Test
    void aFailedTaskReportsItsMessage(FixtureServer fixture) {
        ServiceAppClient client = client(fixture, "failure-failed");
        String id = client.submit(request());

        TaskStatus status = null;
        for (int i = 0; i < 5; i++) {
            status = client.getTaskStatus(id);
        }

        assertTrue(status.isFailed());
        assertEquals("Algorithm failed: bad input", status.message);
        assertTrue(client.getTaskResult(id).actions().isEmpty());
    }

    @Test
    void aTaskThatNeverFinishesStaysBelow100(FixtureServer fixture) {
        ServiceAppClient client = client(fixture, "failure-never-complete");
        String id = client.submit(request());

        TaskStatus status = null;
        for (int i = 0; i < 6; i++) {
            status = client.getTaskStatus(id);
        }

        assertEquals("processing", status.status);
        assertTrue(status.progress < 100);
    }

    @Test
    void aRefusedSubmissionCarriesTheStatusAndMessage(FixtureServer fixture) {
        ServiceAppException e =
                assertThrows(
                        ServiceAppException.class,
                        () -> client(fixture, "failure-submit-500").submit(request()));

        assertEquals(500, e.httpStatus());
        assertTrue(e.getMessage().contains("fixture refused the task"), e.getMessage());
    }

    @Test
    void anUnknownServiceIs404(FixtureServer fixture) {
        ServiceAppClient client =
                new ServiceAppClient(
                        fixture.baseUrl() + "/no-such-app",
                        new JdkHttpTransport(Duration.ofSeconds(5), Duration.ofSeconds(10)));

        ServiceAppException e = assertThrows(ServiceAppException.class, client::getMetadata);
        assertEquals(404, e.httpStatus());
    }

    @Test
    void aClosedPortIsReportedAsNoResponse() throws Exception {
        int port;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        ServiceAppClient client =
                new ServiceAppClient(
                        "http://127.0.0.1:" + port + "/hello",
                        new JdkHttpTransport(Duration.ofSeconds(2), Duration.ofSeconds(2)));

        ServiceAppException e = assertThrows(ServiceAppException.class, client::getStatus);
        assertEquals(ServiceAppException.NO_RESPONSE, e.httpStatus());
    }
}
