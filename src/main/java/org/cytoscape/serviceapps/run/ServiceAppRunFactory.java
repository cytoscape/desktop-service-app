package org.cytoscape.serviceapps.run;

import java.util.function.Supplier;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.serviceapps.client.ServiceAppClientFactory;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.registry.InstalledServiceApp;
import org.cytoscape.serviceapps.registry.ServiceAppRegistry;
import org.cytoscape.serviceapps.render.RenderServiceAppUI;
import org.cytoscape.serviceapps.request.RequestBuilder;
import org.cytoscape.serviceapps.request.ServiceInput;
import org.cytoscape.view.model.CyNetworkView;

/** Makes runs of installed service apps, reading each app's URL and metadata from the registry. */
public class ServiceAppRunFactory {

    private final ServiceAppRegistry registry;
    private final ServiceAppClientFactory clients;
    private final RequestBuilder requests;
    private final RenderServiceAppUI renderer;
    private final Supplier<RunSettings> settings;
    private final ActiveRuns active;

    public ServiceAppRunFactory(
            ServiceAppRegistry registry,
            ServiceAppClientFactory clients,
            RequestBuilder requests,
            RenderServiceAppUI renderer,
            Supplier<RunSettings> settings,
            ActiveRuns active) {
        this.registry = registry;
        this.clients = clients;
        this.requests = requests;
        this.renderer = renderer;
        this.settings = settings;
        this.active = active;
    }

    /**
     * @throws IllegalStateException if the app isn't installed
     */
    public ServiceAppRun create(
            String appName,
            ServiceInput input,
            CyNetwork target,
            CyNetworkView view,
            RunListener listener) {
        InstalledServiceApp app = registry.require(appName);
        ServiceMetadata meta = registry.requireMeta(appName);
        return new ServiceAppRun(
                new ServiceAppRun.Plan(appName, meta, input, target, view),
                clients.forUrl(app.url()),
                requests,
                renderer,
                settings,
                active,
                listener);
    }
}
