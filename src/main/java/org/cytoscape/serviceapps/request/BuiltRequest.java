package org.cytoscape.serviceapps.request;

import java.util.List;

import org.cytoscape.serviceapps.model.TaskRequest;

/**
 * A task request ready to submit, and the IDs it sends.
 *
 * @param notes what the run's completion message should add about how the request was built, such
 *     as why an NDEx address was left empty
 */
public record BuiltRequest(TaskRequest request, SentIds sent, List<String> notes) {}
