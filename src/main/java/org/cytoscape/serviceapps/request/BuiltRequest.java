package org.cytoscape.serviceapps.request;

import org.cytoscape.serviceapps.model.TaskRequest;

/** A task request ready to submit, and the IDs it sends. */
public record BuiltRequest(TaskRequest request, SentIds sent) {}
