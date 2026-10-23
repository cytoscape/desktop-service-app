package org.cytoscape.serviceapps.testsupport;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Waiting in tests, and a port nothing listens on. */
public final class Waits {

    private Waits() {}

    /** Polls every 10 ms until {@code condition} holds; fails after 10 s. */
    public static void until(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met in 10 s");
            }
            sleep(10);
        }
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    /** A local port that was free a moment ago, so nothing answers on it. */
    public static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("no free port", e);
        }
    }
}
