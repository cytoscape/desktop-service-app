package org.cytoscape.serviceapps.form;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.cytoscape.work.Tunable;
import org.cytoscape.work.swing.GUITunableHandler;
import org.cytoscape.work.swing.GUITunableHandlerFactory;

/**
 * Tells Desktop's Tunables framework how to draw a {@link ServiceAppForm}. Registered once as an
 * OSGi service; returns null for every other type, so it never takes over other Tunables.
 */
@SuppressWarnings("rawtypes")
public class ServiceAppFormHandlerFactory implements GUITunableHandlerFactory {

    @Override
    public GUITunableHandler createTunableHandler(Field field, Object instance, Tunable tunable) {
        if (!ServiceAppForm.class.isAssignableFrom(field.getType())) {
            return null;
        }
        return new ServiceAppFormHandler(field, instance, tunable);
    }

    @Override
    public GUITunableHandler createTunableHandler(
            Method getter, Method setter, Object instance, Tunable tunable) {
        if (!ServiceAppForm.class.isAssignableFrom(getter.getReturnType())) {
            return null;
        }
        return new ServiceAppFormHandler(getter, setter, instance, tunable);
    }
}
