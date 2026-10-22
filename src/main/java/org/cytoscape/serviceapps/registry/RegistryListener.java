package org.cytoscape.serviceapps.registry;

/**
 * Told about every change to the installed service apps.
 *
 * <p>Called on the thread that made the change, after it is saved and outside the registry's lock,
 * so a listener may read the registry. Swing listeners must hop to the EDT themselves.
 */
@FunctionalInterface
public interface RegistryListener {
    void registryChanged(RegistryEvent event);
}
