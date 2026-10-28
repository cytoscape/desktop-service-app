package org.cytoscape.serviceapps.model;

import java.util.List;

import com.google.gson.annotations.JsonAdapter;

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

    /**
     * Types the spec marks experimental, "do not use". {@code ndexUUID} is supported: Desktop fills
     * it with the network's NDEx URL, and the user never sees it.
     */
    public static final List<String> UNSUPPORTED_TYPES = List.of(FILE, ACCESS_TOKEN);

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

    /**
     * Column types a column picker offers: a column matches when it passes any of them. Read from a
     * string or a list; empty or null offers every column.
     */
    @JsonAdapter(MetadataLists.StringOrList.class)
    public List<String> columnTypeFilter;

    public String validationHelp;
    public String validationRegex;
    public Double minValue;
    public Double maxValue;
    public List<String> supportedFileExtensions;

    /**
     * The groups the parameter is shown in, outermost first, as Cytoscape Web's {@code groups}.
     * Malformed groups are read as none.
     */
    @JsonAdapter(MetadataLists.Groups.class)
    public List<String> groups;

    public List<String> columnTypeFilter() {
        return columnTypeFilter == null ? List.of() : columnTypeFilter;
    }

    public List<String> groups() {
        return groups == null ? List.of() : groups;
    }
}
