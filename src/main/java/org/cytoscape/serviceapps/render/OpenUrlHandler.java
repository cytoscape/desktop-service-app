package org.cytoscape.serviceapps.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cytoscape.serviceapps.model.ResultAction;
import org.cytoscape.util.swing.OpenBrowser;

/**
 * {@code openURL}: opens the address in the system web browser when {@code target} is null or
 * absent, as Cytoscape Web opens a new tab. A non-null target (an iframe in Cytoscape Web) has no
 * Desktop equivalent, so it fails the action, as does a browser that can't be opened.
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
        if (!data.isJsonObject()
                || !data.getAsJsonObject().has("url")
                || !data.getAsJsonObject().get("url").isJsonPrimitive()) {
            throw new IllegalArgumentException("openURL data has no url");
        }
        JsonObject object = data.getAsJsonObject();
        String url = object.get("url").getAsString();
        JsonElement target = object.get("target");
        if (target != null && !target.isJsonNull()) {
            throw new IllegalArgumentException(
                    "openURL target '"
                            + (target.isJsonPrimitive() ? target.getAsString() : target.toString())
                            + "' isn't supported in Cytoscape Desktop; only a null or absent target"
                            + " opens the web browser");
        }
        if (!browser.openURL(url, false)) {
            throw new IllegalStateException("no web browser could be opened for " + url);
        }
    }
}
