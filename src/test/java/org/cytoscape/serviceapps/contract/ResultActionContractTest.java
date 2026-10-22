package org.cytoscape.serviceapps.contract;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.FixtureServerExtension;

/**
 * The result action contract through the menu path, in the JVM: the registered menu entry, {@code
 * ServiceAppLauncher}, the dialog submitting its defaults, and the run against the fixture.
 */
@ExtendWith(FixtureServerExtension.class)
class ResultActionContractTest extends ResultActionContract {

    private final FixtureServer fixture;
    @TempDir Path dir;
    private JvmMenuHarness harness;

    ResultActionContractTest(FixtureServer fixture) {
        this.fixture = fixture;
    }

    @AfterEach
    void closeHarness() {
        if (harness != null) {
            harness.close();
        }
    }

    @Override
    protected ContractHarness harness() {
        if (harness == null) {
            harness = new JvmMenuHarness(fixture, dir);
        }
        return harness;
    }
}
