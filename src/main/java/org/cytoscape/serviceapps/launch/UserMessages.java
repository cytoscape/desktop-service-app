package org.cytoscape.serviceapps.launch;

/** Tells the user something, such as why a service app can't run right now. */
@FunctionalInterface
public interface UserMessages {

    void warn(String title, String message);
}
