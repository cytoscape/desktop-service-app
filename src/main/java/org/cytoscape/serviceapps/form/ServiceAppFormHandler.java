package org.cytoscape.serviceapps.form;

import java.awt.BorderLayout;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.swing.JPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cytoscape.work.Tunable;
import org.cytoscape.work.swing.AbstractGUITunableHandler;

/**
 * Draws a {@link ServiceAppForm} Tunable inside Desktop's standard task dialog, modelled on
 * table-import's {@code AttributeMappingParametersHandler}. It renders only the form's spec and
 * writes the entered values back; it reads no stored state.
 */
public class ServiceAppFormHandler extends AbstractGUITunableHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceAppFormHandler.class);

    private ServiceAppForm form;
    private FormPanel formPanel;

    public ServiceAppFormHandler(Field field, Object instance, Tunable tunable) {
        super(field, instance, tunable);
        build();
    }

    public ServiceAppFormHandler(Method getter, Method setter, Object instance, Tunable tunable) {
        super(getter, setter, instance, tunable);
        build();
    }

    private void build() {
        try {
            form = (ServiceAppForm) getValue();
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("could not read the service app form", e);
        }
        formPanel = new FormPanel(form.spec());
        panel = new JPanel(new BorderLayout());
        panel.add(formPanel, BorderLayout.CENTER);
    }

    /** The panel drawn for the form, for tests. */
    public FormPanel formPanel() {
        return formPanel;
    }

    @Override
    public void handle() {
        form.setValues(formPanel.values());
        try {
            setValue(form);
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.warn("Could not store the service app form values", e);
        }
    }
}
