package org.cytoscape.serviceapps.model;

import java.util.List;

/**
 * One input the service asks the user for.
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/parameters">parameters</a>
 */
public class Parameter {

    public static final String TEXT = "text";
    public static final String DROP_DOWN = "dropDown";
    public static final String RADIO = "radio";
    public static final String CHECK_BOX = "checkBox";
    public static final String NODE_COLUMN = "nodeColumn";
    public static final String EDGE_COLUMN = "edgeColumn";
    public static final String FILE = "file";
    public static final String ACCESS_TOKEN = "accessToken";
    public static final String NDEX_UUID = "ndexUUID";

    /** Types the spec marks experimental, "do not use". */
    public static final List<String> UNSUPPORTED_TYPES = List.of(FILE, ACCESS_TOKEN, NDEX_UUID);

    /** The label, and the key the value is sent under. */
    public String displayName;

    public String description;
    public String type;
    public List<String> valueList;

    /**
     * Kept as text. A service may send {@code true} or a number here; they are read as their text
     * form.
     */
    public String defaultValue;

    public String validationType;
    public String columnTypeFilter;
    public String validationHelp;
    public String validationRegex;
    public Double minValue;
    public Double maxValue;
    public List<String> supportedFileExtensions;
}
