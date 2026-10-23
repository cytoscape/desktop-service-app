package org.cytoscape.serviceapps.manager;

import java.awt.event.ActionEvent;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.cytoscape.application.swing.AbstractCyAction;

/**
 * {@code Tools > Service Apps > Install}: opens the App Store's list of service apps in the system
 * browser, where a service app is installed with its Install in Cytoscape Desktop button.
 */
public class InstallServiceAppsAction extends AbstractCyAction {

    public static final String TITLE = "Install";

    private final Supplier<AppStoreLink> appStore;
    private final Consumer<String> openUrl;

    /**
     * @param appStore the App Store, read on each click so a changed setting applies
     * @param openUrl opens a web address in the system browser
     */
    public InstallServiceAppsAction(Supplier<AppStoreLink> appStore, Consumer<String> openUrl) {
        super(TITLE);
        this.appStore = appStore;
        this.openUrl = openUrl;
        setPreferredMenu(ManageServiceAppsAction.MENU);
        setMenuGravity(2.0f);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        openUrl.accept(appStore.get().getServiceList());
    }
}
