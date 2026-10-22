package org.cytoscape.serviceapps.render;

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
 *   <li>Actions are applied in order.
 *   <li>A failing or unknown action is reported, and the remaining actions still run.
 *   <li>An action missing from the app's declared {@code cyWebActions} is logged as a warning and
 *       still applied.
 *   <li>An action that needs a network when none was given fails the run, naming the action.
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
     * @param declared the app's {@code cyWebActions}
     * @throws RenderException if an action needs a network and {@code ui} has none
     */
    public RenderReport render(
            List<ResultAction> actions, List<String> declared, UIInfo ui, TaskMonitor monitor) {
        for (ResultAction action : actions) {
            ActionHandler handler = handlers.get(action.action);
            if (handler != null && handler.needsNetwork() && ui.targetNetwork() == null) {
                throw new RenderException(
                        "The result asks to "
                                + action.action
                                + ", which needs a network, and no network was open.");
            }
        }

        RenderReport report = new RenderReport();
        for (int i = 0; i < actions.size(); i++) {
            ResultAction action = actions.get(i);
            monitor.setStatusMessage("Applying " + action.action);
            monitor.setProgress((double) i / Math.max(1, actions.size()));
            ActionHandler handler = handlers.get(action.action);
            if (handler == null) {
                report.problem("unknown result action '" + action.action + "' was skipped");
                continue;
            }
            if (!declared.contains(action.action)) {
                LOGGER.warn(
                        "The service returned '{}', which its metadata doesn't declare in"
                                + " cyWebActions; applying it anyway",
                        action.action);
            }
            try {
                handler.apply(action.data, ui, report);
                report.applied(action.action);
            } catch (RuntimeException e) {
                LOGGER.warn("Result action {} failed", action.action, e);
                report.problem(action.action + " failed: " + e.getMessage());
            }
        }
        monitor.setProgress(1.0);
        return report;
    }
}
