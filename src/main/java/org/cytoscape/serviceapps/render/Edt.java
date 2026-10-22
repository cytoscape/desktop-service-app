package org.cytoscape.serviceapps.render;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

/** Runs view changes on Swing's event thread, waiting for them to finish. */
final class Edt {

    private Edt() {}

    static void run(Runnable work) {
        if (SwingUtilities.isEventDispatchThread()) {
            work.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(work);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while updating the view", e);
        }
    }
}
