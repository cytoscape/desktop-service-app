package org.cytoscape.serviceapps.model;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Cytoscape Web's key rule for parameters, ported from its {@code parameterKeys} tests. */
class ParameterKeysTest {

    private static Parameter parameter(String displayName, String... groups) {
        Parameter parameter = new Parameter();
        parameter.displayName = displayName;
        parameter.type = Parameter.TEXT;
        parameter.groups = List.of(groups);
        return parameter;
    }

    @Test
    void aUniqueNameIsItsOwnKeyEvenInAGroup() {
        assertEquals(
                List.of("Gap", "Width"),
                ParameterKeys.of(List.of(parameter("Gap", "Nodes"), parameter("Width"))));
    }

    @Test
    void sharedNamesAreKeyedByTheirGroupPath() {
        List<Parameter> parameters =
                List.of(
                        parameter("Gap", "Nodes"),
                        parameter("Gap", "Clusters", "Inner"),
                        parameter("Width"),
                        parameter("Gap"));

        assertEquals(
                List.of("Nodes/Gap", "Clusters/Inner/Gap", "Width", "Gap"),
                ParameterKeys.of(parameters));
        assertEquals(List.of(), ParameterKeys.duplicates(parameters));
    }

    @Test
    void theSameNameInTheSameGroupsStillCollides() {
        List<Parameter> parameters =
                List.of(
                        parameter("Gap", "Nodes"),
                        parameter("Gap", "Nodes"),
                        parameter("Size"),
                        parameter("Size"));

        assertEquals(
                List.of("Nodes/Gap", "Nodes/Gap", "Size", "Size"), ParameterKeys.of(parameters));
        assertEquals(List.of("Nodes/Gap", "Size"), ParameterKeys.duplicates(parameters));
    }
}
