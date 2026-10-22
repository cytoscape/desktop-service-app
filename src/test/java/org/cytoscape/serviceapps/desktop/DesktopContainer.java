package org.cytoscape.serviceapps.desktop;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Cytoscape Desktop running in a Docker container, controlled through the {@code docker} CLI.
 *
 * <p>The image comes from {@code docker/desktop} and is built by the {@code desktopImage} Gradle
 * task, which passes its tag in the {@code desktop.image} system property. The app jar under test
 * is named by {@code desktop.jar}. CyREST is published on a free host port.
 */
public final class DesktopContainer implements AutoCloseable {

    /** How a Cytoscape inside the container reaches services on the test host. */
    public static final String HOST_ALIAS = "host.docker.internal";

    private static final Duration CYREST_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration APP_TIMEOUT = Duration.ofMinutes(3);
    private static final String APPS_DIR = "/root/CytoscapeConfiguration/3/apps/installed/";
    private static final String CONFIG_DIR = "/root/CytoscapeConfiguration/3/";

    private final String name =
            "desktop-service-app-it-" + UUID.randomUUID().toString().substring(0, 8);
    private final String image;
    private final Path jar;
    private final int port;
    private final CyRest cyrest;

    private DesktopContainer(String image, Path jar, int port) {
        this.image = image;
        this.jar = jar;
        this.port = port;
        this.cyrest = new CyRest("http://127.0.0.1:" + port);
    }

    /**
     * Starts Cytoscape, waits for CyREST, installs the app jar, and waits for its commands.
     *
     * @throws IllegalStateException if Docker is unavailable or Cytoscape doesn't come up in time
     */
    public static DesktopContainer start() {
        String image = requiredProperty("desktop.image");
        Path jar = Path.of(requiredProperty("desktop.jar"));
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("app jar not found: " + jar + " (run `make build`)");
        }
        DesktopContainer container = new DesktopContainer(image, jar, freePort());
        try {
            container.run();
            container.awaitCyRest();
            container.installApp();
            container.awaitApp();
        } catch (RuntimeException e) {
            container.printDiagnostics();
            container.close();
            throw e;
        }
        return container;
    }

    public CyRest cyrest() {
        return cyrest;
    }

    /** Restarts the container, as a user restarting Cytoscape, and waits until the app is back. */
    public void restart() {
        docker("restart", name);
        awaitCyRest();
        awaitApp();
    }

    /** Cytoscape's console output and framework log, for assertions and failure reports. */
    public String logs() {
        return docker(
                "exec",
                name,
                "sh",
                "-c",
                "cat "
                        + CONFIG_DIR
                        + "console.log "
                        + CONFIG_DIR
                        + "framework-cytoscape.log 2>/dev/null");
    }

    /** Copies a local file into the container. */
    public void copyIn(Path local, String containerPath) {
        docker("cp", local.toString(), name + ":" + containerPath);
    }

    /** Runs a command in the container and returns its output. */
    public String exec(String... command) {
        List<String> args = new ArrayList<>(List.of("exec", name));
        args.addAll(List.of(command));
        return docker(args.toArray(String[]::new));
    }

    /** Prints the container's Cytoscape logs, so a CI failure explains itself. */
    public void printDiagnostics() {
        try {
            System.out.println("==== Cytoscape logs from container " + name + " ====");
            System.out.println(logs());
            System.out.println("==== end of Cytoscape logs ====");
        } catch (RuntimeException e) {
            System.out.println(
                    "could not read Cytoscape logs from " + name + ": " + e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            docker("rm", "--force", name);
        } catch (RuntimeException e) {
            System.out.println("could not remove container " + name + ": " + e.getMessage());
        }
    }

    private void run() {
        docker(
                "run",
                "--detach",
                "--name",
                name,
                "--add-host=" + HOST_ALIAS + ":host-gateway",
                "--publish",
                "127.0.0.1:" + port + ":1234",
                image);
    }

    private void installApp() {
        docker("exec", name, "mkdir", "-p", APPS_DIR);
        // Cytoscape watches the installed-apps folder and starts a jar copied into it.
        docker("cp", jar.toString(), name + ":" + APPS_DIR);
    }

    private void awaitCyRest() {
        await(CYREST_TIMEOUT, "CyREST to answer on port " + port, cyrest::isUp);
    }

    private void awaitApp() {
        await(APP_TIMEOUT, "the serviceapps commands to be available", cyrest::hasServiceApps);
    }

    private void await(Duration timeout, String what, java.util.function.BooleanSupplier ready) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (ready.getAsBoolean()) {
                return;
            }
            sleep(2000);
        }
        throw new IllegalStateException("timed out after " + timeout + " waiting for " + what);
    }

    /** Runs a docker command and returns its output. */
    private static String docker(String... args) {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(5, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException("timed out: " + String.join(" ", command));
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        String.join(" ", command)
                                + " failed ("
                                + process.exitValue()
                                + "): "
                                + output);
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "could not run docker; make integration-test needs Docker: " + e.getMessage(),
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted: " + String.join(" ", command), e);
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "system property "
                            + name
                            + " is not set; run these tests with make integration-test");
        }
        return value;
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("no free port", e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}
