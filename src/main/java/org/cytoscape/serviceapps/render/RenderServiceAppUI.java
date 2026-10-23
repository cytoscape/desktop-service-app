package org.cytoscape.serviceapps.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.serviceapps.cx.Cx2Support;
import org.cytoscape.serviceapps.model.ResultAction;
import org.cytoscape.util.swing.OpenBrowser;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.TaskMonitor;

/**
 * Applies a task result to Desktop. The one renderer for the menu path and {@code task call}.
 *
 * <ul>
 *   <li>Actions are applied in order, and the first one that can't be applied stops the run.
 *   <li>An action missing from the app's declared {@code cyWebActions} is logged as a warning and
 *       still applied.
 * </ul>
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/result">result</a>
 */
public class RenderServiceAppUI {

    private static final Logger LOGGER = LoggerFactory.getLogger(RenderServiceAppUI.class);

    private final Map<String, ActionHandler> handlers = new LinkedHashMap<>();

    public RenderServiceAppUI(List<ActionHandler> handlers) {
        for (ActionHandler handler : handlers) {
            this.handlers.put(handler.action(), handler);
        }
    }

    /** The renderer with a handler for every result action in the spec. */
    public static RenderServiceAppUI standard(
            CyNetworkViewManager viewManager,
            OpenBrowser browser,
            Cx2Support cx2,
            CyApplicationManager applicationManager) {
        return new RenderServiceAppUI(
                List.of(
                        new SelectionHandler(),
                        new LayoutsHandler(viewManager),
                        new OpenUrlHandler(browser),
                        new TablesHandler(),
                        new AddNetworksHandler(cx2, applicationManager),
                        new AddTablesHandler(),
                        new UpdateNetworkHandler(viewManager)));
    }

    /**
     * Applies the actions in order, failing fast as Cytoscape Web does.
     *
     * <ul>
     *   <li>Before anything is applied: an unknown action, or one that needs a network when none is
     *       targeted, fails with nothing changed.
     *   <li>Then the first action that can't be applied stops the run. Earlier actions stay
     *       applied; later ones aren't applied. The message says which.
     * </ul>
     *
     * @param declared the app's {@code cyWebActions}
     * @throws RenderException with the message the user sees
     */
    public RenderReport render(
            List<ResultAction> actions, List<String> declared, UIInfo ui, TaskMonitor monitor) {
        for (ResultAction action : actions) {
            ActionHandler handler = handlers.get(action.action);
            if (handler == null) {
                throw new RenderException(
                        RenderException.FAILED
                                + ": the result asks for unknown action '"
                                + action.action
                                + "'. Nothing was applied.");
            }
            if (handler.needsNetwork() && ui.targetNetwork() == null) {
                throw new RenderException(
                        RenderException.FAILED
                                + ": the result asks to "
                                + action.action
                                + ", which needs a network, and no network was targeted."
                                + " Nothing was applied.");
            }
        }

        RenderReport report = new RenderReport();
        for (int i = 0; i < actions.size(); i++) {
            ResultAction action = actions.get(i);
            monitor.setStatusMessage("Applying " + action.action);
            monitor.setProgress((double) i / Math.max(1, actions.size()));
            if (!declared.contains(action.action)) {
                LOGGER.warn(
                        "The service returned '{}', which its metadata doesn't declare in"
                                + " cyWebActions; applying it anyway",
                        action.action);
            }
            report.start();
            try {
                handlers.get(action.action).apply(action.data, ui, report);
            } catch (RuntimeException e) {
                LOGGER.warn("Result action {} failed", action.action, e);
                throw new RenderException(failedAt(actions, i, report, e));
            }
            report.finish(action.action);
        }
        monitor.setProgress(1.0);
        return report;
    }

    private static String failedAt(
            List<ResultAction> actions, int index, RenderReport report, RuntimeException e) {
        String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        StringBuilder text =
                new StringBuilder(RenderException.FAILED)
                        .append(" at ")
                        .append(actions.get(index).action)
                        .append(" (")
                        .append(index + 1)
                        .append(" of ")
                        .append(actions.size())
                        .append("): ")
                        .append(
                                reason.endsWith(".")
                                        ? reason.substring(0, reason.length() - 1)
                                        : reason)
                        .append(".");
        List<String> notApplied = new ArrayList<>();
        for (int i = index + 1; i < actions.size(); i++) {
            notApplied.add(actions.get(i).action);
        }
        if (report.applied().isEmpty()) {
            text.append(" Nothing was applied.");
        } else {
            text.append(" Applied: ").append(String.join(", ", report.applied())).append(".");
        }
        if (!notApplied.isEmpty()) {
            text.append(" Not applied: ").append(String.join(", ", notApplied)).append(".");
        }
        return text.toString();
    }
}
