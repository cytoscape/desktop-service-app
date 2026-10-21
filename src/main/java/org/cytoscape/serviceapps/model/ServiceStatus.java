package org.cytoscape.serviceapps.model;

/**
 * A service's health, returned by {@code GET /status}.
 *
 * @see <a
 *     href="https://github.com/cytoscape/cytoscape-web/wiki/Specification-for-Service-App-in-Cytoscape-Web-(draft-v2)">Specification,
 *     endpoint 2</a>
 */
public class ServiceStatus {

    public String status;
    public String version;
    public Integer numberOfRunningTasks;

    /** The HTTP status of the reply. Set by the client; not part of the JSON. */
    public transient int httpStatus;
}
