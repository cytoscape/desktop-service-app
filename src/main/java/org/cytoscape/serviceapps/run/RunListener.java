package org.cytoscape.serviceapps.run;

/**
 * Hears a run's state, in the spec's Task Status terms: {@code submitted}, {@code processing},
 * {@code complete} (only after the result is applied) or {@code failed}.
 */
@FunctionalInterface
public interface RunListener {

    RunListener NONE = (status, progress, message) -> {};

    /**
     * @param progress 0 to 100
     * @param message what is happening, or why the run failed
     */
    void stateChanged(String status, double progress, String message);
}
