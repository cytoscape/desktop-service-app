package org.cytoscape.serviceapps.contract;

import org.junit.jupiter.api.Test;

import org.cytoscape.serviceapps.contract.ContractHarness.Ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The result action contract for entry paths that can target one view: the menu path, and {@code
 * task call} with the view in {@code uiInput}.
 */
public abstract class ViewTargetResultActionContract extends ResultActionContract {

    @Test
    void updateLayoutsWithATargetViewChangesOnlyThatView() {
        ContractHarness h = harness();
        h.addTargetView();
        double[] secondBefore = h.position(1, 7);

        succeed("result-update-layouts", Ui.CURRENT_VIEW);

        assertArrayEquals(new double[] {25.2, -473.6}, xy(0, 7));
        assertArrayEquals(secondBefore, h.position(1, 7));
    }
}
