package org.cytoscape.serviceapps.run;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.cytoscape.work.TaskMonitor;

/** A task monitor for runs with no progress window: it keeps the latest progress and messages. */
public class RecordingTaskMonitor implements TaskMonitor {

    private volatile String title;
    private volatile double progress;
    private volatile String statusMessage;
    private final List<String> messages = new CopyOnWriteArrayList<>();

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public void setProgress(double progress) {
        this.progress = progress;
    }

    @Override
    public void setStatusMessage(String message) {
        this.statusMessage = message;
    }

    @Override
    public void showMessage(Level level, String message) {
        messages.add(level + ": " + message);
    }

    public String title() {
        return title;
    }

    public double progress() {
        return progress;
    }

    public String statusMessage() {
        return statusMessage;
    }

    public List<String> messages() {
        return List.copyOf(messages);
    }
}
