package org.cytoscape.serviceapps.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.model.ResultAction;
import org.cytoscape.util.swing.OpenBrowser;

/**
 * {@code openURL}: opens the address in the user's web browser when {@code target} is null or
 * absent. A string target (reserved for iframes in Cytoscape Web) is noted and not opened.
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/openURL-Example">example</a>
 */
public class OpenUrlHandler implements ActionHandler {

    private final OpenBrowser browser;

    public OpenUrlHandler(OpenBrowser browser) {
        this.browser = browser;
    }

    @Override
    public String action() {
        return ResultAction.OPEN_URL;
    }

    @Override
    public boolean needsNetwork() {
        return false;
    }

    @Override
    public void apply(JsonElement data, UIInfo ui, RenderReport report) {
        JsonObject object = data.getAsJsonObject();
        String url = object.get("url").getAsString();
        JsonElement target = object.get("target");
        if (target != null && !target.isJsonNull()) {
            report.note("openURL with target '" + target.getAsString() + "' was not opened");
            return;
        }
        browser.openURL(url);
    }
}
