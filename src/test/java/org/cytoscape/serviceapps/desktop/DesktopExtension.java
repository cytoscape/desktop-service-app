package org.cytoscape.serviceapps.desktop;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestWatcher;

import org.cytoscape.serviceapps.fixture.FixtureServer;

/**
 * Gives Desktop integration tests a running Cytoscape ({@link DesktopContainer}) and a fixture
 * service app it can reach ({@link FixtureServer}).
 *
 * <p>One container and one fixture are started for the whole test run and shared by every test
 * class, since starting Cytoscape is the slow part. Both are stopped when the run ends. When a test
 * fails, Cytoscape's logs are printed.
 */
public class DesktopExtension implements ParameterResolver, TestWatcher {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DesktopExtension.class);

    /** Closes a resource when the JUnit run ends. */
    private record Closing<T extends AutoCloseable>(T resource)
            implements ExtensionContext.Store.CloseableResource {
        @Override
        public void close() throws Exception {
            resource.close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        return type == DesktopContainer.class || type == FixtureServer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == DesktopContainer.class
                ? container(context)
                : fixture(context);
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        Closing<?> running =
                context.getRoot().getStore(NAMESPACE).get(DesktopContainer.class, Closing.class);
        if (running != null) {
            ((DesktopContainer) running.resource()).printDiagnostics();
        }
    }

    @SuppressWarnings("unchecked")
    static DesktopContainer container(ExtensionContext context) {
        return ((Closing<DesktopContainer>)
                        context.getRoot()
                                .getStore(NAMESPACE)
                                .getOrComputeIfAbsent(
                                        DesktopContainer.class,
                                        key -> new Closing<>(DesktopContainer.start()),
                                        Closing.class))
                .resource();
    }

    @SuppressWarnings("unchecked")
    static FixtureServer fixture(ExtensionContext context) {
        return ((Closing<FixtureServer>)
                        context.getRoot()
                                .getStore(NAMESPACE)
                                .getOrComputeIfAbsent(
                                        FixtureServer.class,
                                        key -> {
                                            try {
                                                return new Closing<>(
                                                        FixtureServer.startOnAllInterfaces(0));
                                            } catch (IOException e) {
                                                throw new UncheckedIOException(e);
                                            }
                                        },
                                        Closing.class))
                .resource();
    }

    /** The URL a Cytoscape inside the container uses to reach a fixture scenario. */
    public static String containerUrl(FixtureServer fixture, String scenario) {
        return "http://" + DesktopContainer.HOST_ALIAS + ":" + fixture.port() + "/" + scenario;
    }
}
