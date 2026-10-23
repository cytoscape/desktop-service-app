package org.cytoscape.serviceapps.manager;

import java.awt.event.ActionEvent;
import java.util.function.Supplier;

import org.cytoscape.application.swing.AbstractCyAction;

/**
 * {@code Tools > Service Apps > Manage}: always present, whether or not any service apps are
 * installed. Opens the Service Apps dialog, or brings it to the front if it is open.
 */
public class ManageServiceAppsAction extends AbstractCyAction {

    public static final String MENU = "Tools.Service Apps";
    public static final String TITLE = "Manage";

    private final Supplier<ServiceAppManagerDialog> dialogs;

    /** The dialog shown last; read and written on the EDT only. */
    private ServiceAppManagerDialog current;

    public ManageServiceAppsAction(Supplier<ServiceAppManagerDialog> dialogs) {
        super(TITLE);
        this.dialogs = dialogs;
        setPreferredMenu(MENU);
        setMenuGravity(1.0f);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (current != null && current.isDisplayable()) {
            current.toFront();
            return;
        }
        current = dialogs.get();
        current.setVisible(true);
    }

    /** The dialog shown last, for tests. */
    ServiceAppManagerDialog dialog() {
        return current;
    }
}
