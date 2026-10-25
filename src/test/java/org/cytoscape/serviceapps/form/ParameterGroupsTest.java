package org.cytoscape.serviceapps.form;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Nesting by {@code groups}, as Cytoscape Web's {@code groupParameters} nests its fieldsets. */
class ParameterGroupsTest {

    private static ControlSpec control(String key, String... groups) {
        return new ControlSpec(
                key,
                ControlKind.TEXT,
                key,
                null,
                List.of(),
                List.of(""),
                false,
                null,
                null,
                List.of(groups));
    }

    /** The tree as text: keys for controls, [name: children] for groups. */
    private static String shape(ParameterGroups.Group group) {
        StringBuilder text = new StringBuilder();
        for (ParameterGroups.Child child : group.children()) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            if (child instanceof ParameterGroups.Control control) {
                text.append(control.spec().key());
            } else {
                ParameterGroups.Group subgroup = (ParameterGroups.Group) child;
                text.append('[').append(subgroup.name()).append(": ").append(shape(subgroup));
                text.append(']');
            }
        }
        return text.toString();
    }

    @Test
    void nestsByGroupsKeepingAGroupTogetherInFirstSeenOrder() {
        ParameterGroups.Group root =
                ParameterGroups.of(
                        List.of(
                                control("Last name", "Company", "Department", "Office"),
                                control(
                                        "First name",
                                        "Company",
                                        "Department",
                                        "Office",
                                        "Identity"),
                                control("Top level"),
                                control("Office name", "Company", "Department", "Office")));

        assertEquals(
                "[Company: [Department: [Office: Last name, [Identity: First name], Office name]]],"
                        + " Top level",
                shape(root));
        ParameterGroups.Group company =
                assertInstanceOf(ParameterGroups.Group.class, root.children().get(0));
        ParameterGroups.Group department =
                assertInstanceOf(ParameterGroups.Group.class, company.children().get(0));
        assertEquals(List.of("Company", "Department"), department.path());
    }

    @Test
    void ungroupedControlsStayAtTheTopLevelInOrder() {
        assertEquals(
                "a, b, c",
                shape(ParameterGroups.of(List.of(control("a"), control("b"), control("c")))));
        assertEquals("", ParameterGroups.of(List.of()).name());
    }
}
