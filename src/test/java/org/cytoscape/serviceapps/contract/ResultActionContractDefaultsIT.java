package org.cytoscape.serviceapps.contract;

import org.junit.jupiter.api.extension.ExtendWith;

import org.cytoscape.serviceapps.desktop.DesktopContainer;
import org.cytoscape.serviceapps.desktop.DesktopExtension;
import org.cytoscape.serviceapps.fixture.FixtureServer;

/**
 * The result action contract through {@code serviceapps task call} with {@code uiInput} omitted:
 * the target is the active network and every one of its views. Targeting one view is covered by
 * {@link ViewTargetResultActionContract}.
 */
@ExtendWith(DesktopExtension.class)
class ResultActionContractDefaultsIT extends ResultActionContract {

    private final DockerTaskCallHarness harness;

    ResultActionContractDefaultsIT(DesktopContainer desktop, FixtureServer fixture) {
        harness = new DockerTaskCallHarness(desktop, fixture, true);
    }

    @Override
    protected ContractHarness harness() {
        return harness;
    }
}
