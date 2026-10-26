package org.cytoscape.serviceapps.model;

import java.util.List;

/**
 * What data the service wants sent with each task.
 *
 * @see <a
 *     href="https://github.com/cytoscape/cytoscape-web/wiki/serviceInputDefinition">serviceInputDefinition</a>
 */
public class ServiceInputDefinition {

    /** {@code node}, {@code edge}, {@code network}, and {@code none} or null for no data. */
    public String type;

    /** {@code dynamic} (the default), {@code selected} or {@code all}. */
    public String scope;

    public List<InputColumn> inputColumns;
    public InputNetwork inputNetwork;

    public List<InputColumn> inputColumns() {
        return inputColumns == null ? List.of() : inputColumns;
    }

    /**
     * One node or edge column to send.
     *
     * @see <a
     *     href="https://github.com/cytoscape/cytoscape-web/wiki/serviceInputDefinition.inputColumns">inputColumns</a>
     */
    public static class InputColumn {
        public String name;
        public String description;
        public String dataType;
        public Boolean allowMultipleSelection;
        public String defaultColumnName;

        /** A column that is always sent, with no control shown. */
        public String columnName;
    }

    /**
     * How the network is sent.
     *
     * @see <a
     *     href="https://github.com/cytoscape/cytoscape-web/wiki/serviceInputDefinition.inputNetwork">inputNetwork</a>
     */
    public static class InputNetwork {
        /** {@code network} (full CX2) or {@code graph} (IDs only). */
        public String model;

        /** {@code cx2}, or {@code edgelist}, which the spec marks as not implemented. */
        public String format;
    }
}
