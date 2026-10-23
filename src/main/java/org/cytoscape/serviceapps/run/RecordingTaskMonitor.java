package org.cytoscape.serviceapps.run;

import org.cytoscape.work.TaskMonitor;

/** A task monitor for runs with no progress window: it keeps the latest progress and status. */
public class RecordingTaskMonitor implements TaskMonitor {

    private volatile double progress;
    private volatile String statusMessage;

    @Override
    public void setTitle(String title) {}

    @Override
    public void setProgress(double progress) {
        this.progress = progress;
    }

    @Override
    public void setStatusMessage(String message) {
        this.statusMessage = message;
    }

    /** Messages reach the caller through the run's listener instead. */
    @Override
    public void showMessage(Level level, String message) {}

    public double progress() {
        return progress;
    }

    public String statusMessage() {
        return statusMessage;
    }
}
