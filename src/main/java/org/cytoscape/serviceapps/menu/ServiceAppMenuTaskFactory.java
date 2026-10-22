package org.cytoscape.serviceapps.menu;

import org.cytoscape.serviceapps.launch.ServiceAppLauncher;
import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;

/**
 * One service app's menu entry. A runnable app's entry is enabled by Desktop's {@code ENABLE_FOR};
 * an app that can't run gets an entry with no {@code ENABLE_FOR}, which Desktop greys out because
 * {@link #isReady()} is false.
 */
public class ServiceAppMenuTaskFactory extends AbstractTaskFactory {

    private final String appName;
    private final boolean runnable;
    private final ServiceAppLauncher launcher;

    public ServiceAppMenuTaskFactory(
            String appName, boolean runnable, ServiceAppLauncher launcher) {
        this.appName = appName;
        this.runnable = runnable;
        this.launcher = launcher;
    }

    public String appName() {
        return appName;
    }

    @Override
    public boolean isReady() {
        return runnable;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return launcher.createTaskIterator(appName);
    }
}
