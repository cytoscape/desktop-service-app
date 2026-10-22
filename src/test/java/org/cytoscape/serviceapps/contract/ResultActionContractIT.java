package org.cytoscape.serviceapps.contract;

import org.junit.jupiter.api.extension.ExtendWith;

import org.cytoscape.serviceapps.desktop.DesktopContainer;
import org.cytoscape.serviceapps.desktop.DesktopExtension;
import org.cytoscape.serviceapps.fixture.FixtureServer;

/**
 * The result action contract through {@code serviceapps task call} against real Cytoscape in
 * Docker, with the target passed in {@code uiInput}. Same scenarios and assertions as the menu
 * path's {@code ResultActionContractTest}.
 */
@ExtendWith(DesktopExtension.class)
class ResultActionContractIT extends ResultActionContract {

    private final DockerTaskCallHarness harness;

    ResultActionContractIT(DesktopContainer desktop, FixtureServer fixture) {
        harness = new DockerTaskCallHarness(desktop, fixture, false);
    }

    @Override
    protected ContractHarness harness() {
        return harness;
    }
}
