package org.cytoscape.serviceapps.desktop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs a command with a time limit. The output is read on its own thread, so a command that hangs
 * without exiting still hits the limit and is killed.
 */
final class TimedProcess {

    /** How a command ended. */
    record Result(int exitCode, String output) {}

    private TimedProcess() {}

    /**
     * @throws IllegalStateException if the command takes longer than {@code timeout}
     * @throws IOException if the command can't be started
     */
    static Result run(List<String> command, Duration timeout)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        CompletableFuture<String> output =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return new String(
                                        process.getInputStream().readAllBytes(),
                                        StandardCharsets.UTF_8);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException(
                    "timed out after " + timeout + ": " + String.join(" ", command));
        }
        try {
            return new Result(process.exitValue(), output.get(30, TimeUnit.SECONDS));
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("could not read the output of " + String.join(" ", command), e);
        }
    }
}
