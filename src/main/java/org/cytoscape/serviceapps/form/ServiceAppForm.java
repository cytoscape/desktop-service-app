package org.cytoscape.serviceapps.form;

/**
 * The Tunable value type for a service app's dialog. A run task declares {@code @Tunable(context =
 * "gui") public ServiceAppForm form}; {@link ServiceAppFormHandler} draws it from {@link #spec()}
 * and writes the entered values back with {@link #setValues}.
 */
public class ServiceAppForm {

    private final FormSpec spec;
    private volatile FormValues values;

    public ServiceAppForm(FormSpec spec) {
        this.spec = spec;
        this.values = spec.initialValues();
    }

    public FormSpec spec() {
        return spec;
    }

    public FormValues values() {
        return values;
    }

    public void setValues(FormValues values) {
        this.values = values;
    }
}
