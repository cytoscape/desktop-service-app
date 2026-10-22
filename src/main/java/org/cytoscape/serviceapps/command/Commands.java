package org.cytoscape.serviceapps.command;

import java.lang.reflect.Field;
import java.util.List;

import org.cytoscape.work.Task;

/**
 * Every {@code serviceapps} command and its metadata. Each command class declares {@code NAME},
 * {@code DESCRIPTION}, {@code LONG_DESCRIPTION} and {@code EXAMPLE_JSON}; Desktop's {@code help},
 * CyREST's command swagger and the MCP Desktop app show them.
 */
public final class Commands {

    public static final String NAMESPACE = "serviceapps";

    /** The commands, in the order they are registered. */
    public static final List<Class<? extends Task>> ALL =
            List.of(
                    ListCommandTask.class,
                    InstallCommandTask.class,
                    UninstallCommandTask.class,
                    StatusCommandTask.class,
                    DescribeCommandTask.class,
                    TaskCallCommandTask.class,
                    TaskStatusCommandTask.class);

    /** One command's registration metadata. */
    public record Command(
            String name, String description, String longDescription, String exampleJson) {

        public static Command of(Class<? extends Task> type) {
            return new Command(
                    constant(type, "NAME"),
                    constant(type, "DESCRIPTION"),
                    constant(type, "LONG_DESCRIPTION"),
                    constant(type, "EXAMPLE_JSON"));
        }
    }

    private Commands() {}

    private static String constant(Class<?> type, String name) {
        try {
            Field field = type.getField(name);
            return (String) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(type.getSimpleName() + " declares no " + name, e);
        }
    }
}
