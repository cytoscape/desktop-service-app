package org.cytoscape.serviceapps.desktop;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Docker helper's time limit actually fires for a command that never exits. */
class TimedProcessTest {

    @Test
    void returnsTheExitCodeAndOutput() throws Exception {
        TimedProcess.Result result =
                TimedProcess.run(List.of("sh", "-c", "echo hello; exit 3"), Duration.ofSeconds(10));

        assertEquals(3, result.exitCode());
        assertEquals("hello", result.output().strip());
    }

    @Test
    void aCommandThatHangsIsStoppedAtTheLimit() {
        IllegalStateException e =
                assertTimeoutPreemptively(
                        Duration.ofSeconds(10),
                        () ->
                                assertThrows(
                                        IllegalStateException.class,
                                        () ->
                                                TimedProcess.run(
                                                        List.of(
                                                                "sh",
                                                                "-c",
                                                                "echo started; sleep 60"),
                                                        Duration.ofMillis(300))));

        assertTrue(e.getMessage().startsWith("timed out after PT0.3S"), e.getMessage());
    }
}
