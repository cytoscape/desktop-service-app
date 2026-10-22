package org.cytoscape.serviceapps.form;

/** The kind of dialog control a parameter or input column becomes. */
public enum ControlKind {
    /** A text field ({@code text}). */
    TEXT,
    /** A drop-down list ({@code dropDown}). */
    DROP_DOWN,
    /** A group of radio buttons ({@code radio}). */
    RADIO,
    /** A checkbox ({@code checkBox}); its value is {@code "true"} or {@code "false"}. */
    CHECK_BOX,
    /** A drop-down of node columns ({@code nodeColumn}). */
    NODE_COLUMN,
    /** A drop-down of edge columns ({@code edgeColumn}). */
    EDGE_COLUMN,
    /** A picker for one input column; a multi-select list when the app allows several. */
    INPUT_COLUMN
}
