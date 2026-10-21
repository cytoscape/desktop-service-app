package org.cytoscape.serviceapps.fixture;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Starts a {@link FixtureServer} on a free port for a test class and injects it into test methods
 * and constructors. The request log is cleared before each test.
 *
 * <pre>
 * &#64;ExtendWith(FixtureServerExtension.class)
 * class MyIT {
 *     &#64;Test void installs(FixtureServer fixture) { ... fixture.url("hello") ... }
 * }
 * </pre>
 */
public class FixtureServerExtension
        implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(FixtureServerExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        context.getStore(NAMESPACE).put(FixtureServer.class, FixtureServer.start(0));
    }

    @Override
    public void afterAll(ExtensionContext context) {
        FixtureServer server =
                context.getStore(NAMESPACE).remove(FixtureServer.class, FixtureServer.class);
        if (server != null) {
            server.close();
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        server(context).clearRequests();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == FixtureServer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return server(context);
    }

    /** A method context's store falls back to its class context, where the server was put. */
    private static FixtureServer server(ExtensionContext context) {
        return context.getStore(NAMESPACE).get(FixtureServer.class, FixtureServer.class);
    }
}
