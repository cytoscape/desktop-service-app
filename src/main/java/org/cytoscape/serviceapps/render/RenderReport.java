package org.cytoscape.serviceapps.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What happened while applying a result: the actions applied and any notes. A failing action
 * doesn't appear here: it stops the run with a {@link RenderException}.
 */
public class RenderReport {

    private final List<String> applied = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private boolean skipped;

    void start() {
        skipped = false;
    }

    void finish(String action) {
        if (!skipped) {
            applied.add(action);
        }
    }

    /** Something the user should know that didn't stop the action, such as skipped rows. */
    public void note(String message) {
        notes.add(message);
    }

    /** The action was deliberately not applied, such as addTables; the run still completes. */
    public void skip(String message) {
        notes.add(message);
        skipped = true;
    }

    public List<String> applied() {
        return Collections.unmodifiableList(applied);
    }

    public List<String> notes() {
        return Collections.unmodifiableList(notes);
    }

    /** One line for the run's status message. */
    public String summary() {
        StringBuilder text = new StringBuilder();
        text.append(
                applied.isEmpty()
                        ? "No result actions applied"
                        : "Applied " + String.join(", ", applied));
        if (!notes.isEmpty()) {
            text.append(". Notes: ").append(String.join("; ", notes));
        }
        return text.toString();
    }
}
