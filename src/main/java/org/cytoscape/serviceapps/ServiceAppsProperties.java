package org.cytoscape.serviceapps;

import org.cytoscape.property.AbstractConfigDirPropsReader;
import org.cytoscape.property.CyProperty;

/**
 * The app's settings, {@code serviceapps.props} in the Cytoscape configuration directory. Defaults
 * come from the bundled file of the same name. Editable in Edit &gt; Preferences &gt; Properties.
 */
public class ServiceAppsProperties extends AbstractConfigDirPropsReader {

    public static final String NAME = "serviceapps";
    public static final String FILE_NAME = "serviceapps.props";

    public ServiceAppsProperties() {
        super(NAME, FILE_NAME, CyProperty.SavePolicy.CONFIG_DIR);
    }
}
