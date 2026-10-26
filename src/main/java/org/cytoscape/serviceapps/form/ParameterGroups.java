package org.cytoscape.serviceapps.form;

import java.util.ArrayList;
import java.util.List;

/**
 * Nests a dialog's parameter controls by their {@code groups}, as Cytoscape Web's form does: a
 * control with groups {@code [A, B]} goes under A, then B. At every level, controls and subgroups
 * keep the order they first appear in, so a group's controls stay together even when the metadata
 * interleaves them.
 */
public final class ParameterGroups {

    /** A control, or a group of them. */
    public sealed interface Child permits Control, Group {}

    public record Control(ControlSpec spec) implements Child {}

    /**
     * @param path the group names from the outermost group down; empty for the top level
     */
    public record Group(List<String> path, List<Child> children) implements Child {

        /** The group's own name; empty for the top level. */
        public String name() {
            return path.isEmpty() ? "" : path.get(path.size() - 1);
        }
    }

    private ParameterGroups() {}

    /** The top level, holding ungrouped controls and the outermost groups. */
    public static Group of(List<ControlSpec> controls) {
        Group root = new Group(List.of(), new ArrayList<>());
        for (ControlSpec control : controls) {
            Group group = root;
            for (String name : control.groups()) {
                group = subgroup(group, name);
            }
            group.children().add(new Control(control));
        }
        return root;
    }

    private static Group subgroup(Group parent, String name) {
        for (Child child : parent.children()) {
            if (child instanceof Group group && group.name().equals(name)) {
                return group;
            }
        }
        List<String> path = new ArrayList<>(parent.path());
        path.add(name);
        Group group = new Group(List.copyOf(path), new ArrayList<>());
        parent.children().add(group);
        return group;
    }
}
