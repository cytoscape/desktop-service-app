package org.cytoscape.serviceapps.client;

import org.cytoscape.serviceapps.model.ServiceStatus;

/**
 * The live check of whether a service app answers, used by {@code serviceapps status} and by the
 * Service Apps manager so they agree.
 */
public class ServiceAppStatusChecker {

    /**
     * @param httpStatus the reply's HTTP status, or null when nothing answered
     * @param serviceStatus the status the service reported, such as {@code ok}, or null
     * @param message why the check failed, or null
     */
    public record Result(
            boolean reachable, Integer httpStatus, String serviceStatus, String message) {}

    private final ServiceAppClientFactory clients;

    public ServiceAppStatusChecker(ServiceAppClientFactory clients) {
        this.clients = clients;
    }

    /** Calls the app's {@code GET /status}. The app is reachable when that returns 2xx. */
    public Result check(String url) {
        try {
            ServiceStatus status = clients.forUrl(url).getStatus();
            return new Result(true, status.httpStatus, status.status, null);
        } catch (ServiceAppException e) {
            Integer http =
                    e.httpStatus() == ServiceAppException.NO_RESPONSE ? null : e.httpStatus();
            return new Result(false, http, null, e.getMessage());
        } catch (IllegalArgumentException e) {
            return new Result(false, null, null, e.getMessage());
        }
    }
}
