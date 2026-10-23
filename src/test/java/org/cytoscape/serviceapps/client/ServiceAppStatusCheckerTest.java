package org.cytoscape.serviceapps.client;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A service is reachable when GET /status answers 2xx, whatever its body. */
class ServiceAppStatusCheckerTest {

    private static final String URL = "https://h.example/hello";

    private final FakeTransport transport = new FakeTransport();
    private final ServiceAppStatusChecker checker =
            new ServiceAppStatusChecker(url -> new ServiceAppClient(url, transport));

    @Test
    void aStatusObjectIsReachableWithTheServicesStatus() {
        transport.reply(200, "{\"status\":\"ok\"}");

        ServiceAppStatusChecker.Result result = checker.check(URL);

        assertTrue(result.reachable());
        assertEquals(200, result.httpStatus());
        assertEquals("ok", result.serviceStatus());
        assertNull(result.message());
    }

    @Test
    void a2xxReplyWithoutAStatusObjectIsStillReachable() {
        for (int status : new int[] {200, 204}) {
            for (String body : new String[] {"", "<html>ok</html>"}) {
                transport.reply(status, body);

                ServiceAppStatusChecker.Result result = checker.check(URL);

                assertTrue(result.reachable(), status + " " + body);
                assertEquals(status, result.httpStatus());
                assertNull(result.serviceStatus());
                assertTrue(result.message() != null && !result.message().isBlank());
            }
        }
    }

    @Test
    void anErrorReplyIsUnreachable() {
        transport.reply(503, "{\"message\":\"down\"}");

        ServiceAppStatusChecker.Result result = checker.check(URL);

        assertFalse(result.reachable());
        assertEquals(503, result.httpStatus());
    }

    @Test
    void noReplyIsUnreachable() {
        transport.fail(new IOException("connection refused"));

        ServiceAppStatusChecker.Result result = checker.check(URL);

        assertFalse(result.reachable());
        assertNull(result.httpStatus());
    }

    @ParameterizedTest(name = "HTTP {0}")
    @CsvSource({"199, false", "200, true", "299, true", "300, false", "301, false", "304, false"})
    void onlyA2xxReplyIsReachable(int status, boolean reachable) {
        transport.reply(status, "{\"status\":\"ok\"}");

        ServiceAppStatusChecker.Result result = checker.check(URL);

        assertEquals(reachable, result.reachable());
        assertEquals(status, result.httpStatus());
    }
}
