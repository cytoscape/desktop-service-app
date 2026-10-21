package org.cytoscape.serviceapps.model;

import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * What a service app says about itself, returned by {@code GET /}.
 *
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/Service-Metadata">Service
 *     Metadata</a>
 */
public class ServiceMetadata {

    public String name;
    public String description;
    public Boolean showDescriptionInDialog;
    public String author;
    public String citation;
    public String version;

    /** Cytoscape Web's model calls this {@code cyWebAction}; both spellings are read. */
    @SerializedName(
            value = "cyWebActions",
            alternate = {"cyWebAction"})
    public List<String> cyWebActions;

    public CyWebMenuItem cyWebMenuItem;
    public ServiceInputDefinition serviceInputDefinition;
    public List<Parameter> parameters;

    public List<String> cyWebActions() {
        return cyWebActions == null ? List.of() : cyWebActions;
    }

    public List<Parameter> parameters() {
        return parameters == null ? List.of() : parameters;
    }
}
