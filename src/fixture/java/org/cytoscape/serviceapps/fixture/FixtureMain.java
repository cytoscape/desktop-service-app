package org.cytoscape.serviceapps.fixture;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Runs the hello-world service app until stopped, for manual testing: {@code make fixture}.
 *
 * <p>Install one into a running Cytoscape with, for example: {@code curl
 * "http://127.0.0.1:1234/v1/commands/serviceapps/install?name=Hello%20World&version=1.0&url=http://127.0.0.1:8765/hello"}
 */
public final class FixtureMain {

    private FixtureMain() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8765;
        FixtureServer server = FixtureServer.start(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));

        System.out.println(
                "Hello-world service app fixture on "
                        + server.baseUrl()
                        + " (catalogue v"
                        + ScenarioCatalog.CATALOG_VERSION
                        + ")");
        for (Scenario scenario : ScenarioCatalog.all().values()) {
            System.out.printf(
                    "  %-32s %s  name='%s' version=%s%n",
                    scenario.name(),
                    server.url(scenario.name()),
                    scenario.appName(),
                    scenario.appVersion());
        }
        System.out.println("Press Ctrl+C to stop.");
        new CountDownLatch(1).await();
    }
}
