package org.cytoscape.serviceapps.registry;

/** A change to the installed service apps. */
public record RegistryEvent(Type type, String name) {

    public enum Type {
        /** A service app was installed. */
        ADDED,
        /**
         * An installed app was replaced or changed: upgrade, new metadata, reachability, values.
         */
        UPDATED,
        /** A service app was uninstalled. */
        REMOVED
    }
}
