package org.cytoscape.serviceapps.contract;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import org.cytoscape.serviceapps.contract.ContractHarness.Outcome;
import org.cytoscape.serviceapps.contract.ContractHarness.Ui;
import org.cytoscape.serviceapps.fixture.FixtureServer;
import org.cytoscape.serviceapps.fixture.FixtureServerExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The result action contract through the menu path, in the JVM: the registered menu entry, {@code
 * ServiceAppLauncher}, the dialog submitting its defaults, and the run against the fixture.
 */
@ExtendWith(FixtureServerExtension.class)
class ResultActionContractTest extends ViewTargetResultActionContract {

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
    protected JvmMenuHarness harness() {
        if (harness == null) {
            harness = new JvmMenuHarness(fixture, dir);
        }
        return harness;
    }

    /** In the JVM only: Docker's Desktop has no way to make its browser fail. */
    @Test
    void aBrowserThatCantOpenFailsTheRun() {
        harness().failBrowser();

        Outcome outcome = fail("result-open-url", Ui.CURRENT_VIEW);

        assertEquals(
                "Rendering failed at openURL (1 of 1): no web browser could be opened for"
                        + " https://www.cytoscape.org. Nothing was applied.",
                outcome.message());
    }
}
