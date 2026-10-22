package org.cytoscape.serviceapps.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What happened while applying a result: actions applied, notes, and problems. */
public class RenderReport {

    private final List<String> applied = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();

    void applied(String action) {
        applied.add(action);
    }

    /** Something the user should know that didn't stop the action, such as skipped rows. */
    public void note(String message) {
        notes.add(message);
    }

    /** An action that failed or was not understood; the remaining actions still ran. */
    public void problem(String message) {
        problems.add(message);
    }

    public List<String> applied() {
        return Collections.unmodifiableList(applied);
    }

    public List<String> notes() {
        return Collections.unmodifiableList(notes);
    }

    public List<String> problems() {
        return Collections.unmodifiableList(problems);
    }

    /** One line for the run's status message. */
    public String summary() {
        StringBuilder text = new StringBuilder();
        text.append(
                applied.isEmpty()
                        ? "No result actions applied"
                        : "Applied " + String.join(", ", applied));
        if (!problems.isEmpty()) {
            text.append(". Problems: ").append(String.join("; ", problems));
        }
        if (!notes.isEmpty()) {
            text.append(". Notes: ").append(String.join("; ", notes));
        }
        return text.toString();
    }
}
