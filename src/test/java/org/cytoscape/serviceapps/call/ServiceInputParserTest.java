package org.cytoscape.serviceapps.call;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.serviceapps.form.FormSpecBuilder;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.request.ServiceInput;
import org.cytoscape.serviceapps.testsupport.TestNetworks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** task call rejects every input the dialog would reject, naming the field. */
class ServiceInputParserTest {

    private final ServiceInputParser parser = new ServiceInputParser(new FormSpecBuilder());
    private final CyNetwork network = TestNetworks.withEveryColumnType();

    private ServiceInput parse(String scenario, String serviceInput) {
        return parser.parse(serviceInput, TestNetworks.scenario(scenario), network);
    }

    private List<String> rejected(String scenario, String serviceInput) {
        return assertThrows(InvalidInputException.class, () -> parse(scenario, serviceInput))
                .problems();
    }

    private static String params(String body) {
        return "{\"parameters\":{" + body + "}}";
    }

    private static String columns(String body) {
        return "{\"inputColumns\":{" + body + "}}";
    }

    @Test
    void omittedValuesTakeTheDialogsDefaults() {
        assertEquals(Map.of("Greeting", "hello"), parse("hello", null).parameters());

        ServiceInput input =
                parse("input-columns", columns("\"Scores\":\"c_double\",\"Any\":\"name\""));
        assertEquals(Map.of("Greeting", "hello"), input.parameters());
        assertEquals(List.of("name"), input.inputColumns().get("Gene"));
    }

    @Test
    void parametersThatShareANameAreGivenByTheirGroupKeys() {
        ServiceInput input = parse("param-groups", params("\"Clusters/Gap\":\"wide\""));

        assertEquals("n", input.parameters().get("Nodes/Gap"));
        assertEquals("wide", input.parameters().get("Clusters/Gap"));
        assertTrue(
                rejected("param-groups", params("\"Gap\":\"x\""))
                        .get(0)
                        .contains("unknown parameter 'Gap'"));
    }

    @Test
    void givenValuesAreSentAsText() {
        assertEquals(
                "hi", parse("hello", params("\"Greeting\":\"hi\"")).parameters().get("Greeting"));
        assertEquals(
                "0.25",
                parse("param-text-number", params("\"Resolution\":0.25"))
                        .parameters()
                        .get("Resolution"));
        assertEquals(
                "0.25",
                parse("param-text-number", params("\"Resolution\":\"0.25\""))
                        .parameters()
                        .get("Resolution"));
        assertEquals(
                "-3",
                parse("param-text-digits", params("\"Iterations\":-3"))
                        .parameters()
                        .get("Iterations"));
        assertEquals(
                "false",
                parse("param-checkbox-true", params("\"Weighted\":false"))
                        .parameters()
                        .get("Weighted"));
        assertEquals(
                "true",
                parse("param-checkbox-true", params("\"Weighted\":\"TRUE\""))
                        .parameters()
                        .get("Weighted"));
        assertEquals(
                "infomap",
                parse("param-dropdown", params("\"Method\":\"infomap\""))
                        .parameters()
                        .get("Method"));
    }

    @Test
    void numbersOutsideTheirRulesAreRejectedWithTheServicesHelp() {
        assertEquals(
                List.of("Resolution: Must be a number from 0 to 1"),
                rejected("param-text-number", params("\"Resolution\":2")));
        assertEquals(
                List.of("Resolution: Must be a number from 0 to 1"),
                rejected("param-text-number", params("\"Resolution\":\"abc\"")));
        assertEquals(
                List.of("Iterations: Must be a whole number from -10 to 10"),
                rejected("param-text-digits", params("\"Iterations\":2.5")));
        assertEquals(
                List.of("Iterations: Must be a whole number from -10 to 10"),
                rejected("param-text-digits", params("\"Iterations\":11")));
    }

    @Test
    void textMustMatchTheWholeRegex() {
        assertEquals(
                "leiden",
                parse("param-text-string", params("\"Algorithm\":\"leiden\""))
                        .parameters()
                        .get("Algorithm"));
        assertEquals(
                List.of("Algorithm: Must be louvain or leiden"),
                rejected("param-text-string", params("\"Algorithm\":\"leidenx\"")));
    }

    @Test
    void choicesMustBeInTheList() {
        assertEquals(
                List.of("'x' is not a choice for Method"),
                rejected("param-dropdown", params("\"Method\":\"x\"")));
        assertEquals(
                List.of("Choose a value for Method"), rejected("param-dropdown-no-default", null));
        assertEquals(
                List.of("Choose a value for Direction"), rejected("param-radio-no-default", null));
    }

    @Test
    void checkboxesTakeTrueOrFalse() {
        assertEquals(
                List.of("Weighted: must be true or false, not 1"),
                rejected("param-checkbox-true", params("\"Weighted\":1")));
    }

    /** A column-filter scenario cut down to the named parameters. */
    private static ServiceMetadata only(String scenario, String... names) {
        ServiceMetadata meta = TestNetworks.scenario(scenario);
        meta.parameters =
                meta.parameters().stream()
                        .filter(p -> List.of(names).contains(p.displayName))
                        .toList();
        return meta;
    }

    @Test
    void columnParametersMustNameAMatchingColumnOfTheTargetNetwork() {
        ServiceMetadata meta =
                only("param-node-column-filters", "nodeColumn long", "nodeColumn string");

        ServiceInput input = parser.parse(params("\"nodeColumn long\":\"c_long\""), meta, network);
        assertEquals("c_long", input.parameters().get("nodeColumn long"));
        assertEquals("name", input.parameters().get("nodeColumn string"), "default column exists");

        assertEquals(
                List.of("'c_string' is not a choice for nodeColumn long"),
                assertThrows(
                                InvalidInputException.class,
                                () ->
                                        parser.parse(
                                                params("\"nodeColumn long\":\"c_string\""),
                                                meta,
                                                network))
                        .problems());
        ServiceMetadata edges = only("param-edge-column-filters", "edgeColumn any");
        assertEquals(
                List.of("'c_string' is not a choice for edgeColumn any"),
                assertThrows(
                                InvalidInputException.class,
                                () ->
                                        parser.parse(
                                                params("\"edgeColumn any\":\"c_string\""),
                                                edges,
                                                network))
                        .problems(),
                "an edge picker never takes a node column");
    }

    @Test
    void inputColumnsTakeOneNameOrAListWhenSeveralAreAllowed() {
        ServiceInput input =
                parse(
                        "input-columns",
                        columns("\"Scores\":[\"c_double\",\"c_integer\"],\"Any\":\"c_boolean\""));
        assertEquals(List.of("c_double", "c_integer"), input.inputColumns().get("Scores"));
        assertEquals(List.of("c_boolean"), input.inputColumns().get("Any"));

        assertEquals(
                List.of("Choose one value for Gene"),
                rejected(
                        "input-columns",
                        columns(
                                "\"Gene\":[\"name\",\"c_string\"],\"Scores\":\"c_double\",\"Any\":\"name\"")));
        assertEquals(
                List.of("'c_string' is not a choice for Scores"),
                rejected("input-columns", columns("\"Scores\":[\"c_string\"],\"Any\":\"name\"")));
    }

    @Test
    void requiredValuesWithNoDefaultMustBeGiven() {
        List<String> problems = rejected("input-columns", null);

        assertEquals(List.of("Choose a value for Scores", "Choose a value for Any"), problems);
    }

    @Test
    void anAlwaysSentColumnMustExistOnTheTargetNetwork() {
        ServiceMetadata meta = TestNetworks.scenario("input-columns");
        meta.serviceInputDefinition.inputColumns.get(3).columnName = "missing";

        List<String> problems =
                assertThrows(
                                InvalidInputException.class,
                                () ->
                                        parser.parse(
                                                columns("\"Scores\":\"c_double\",\"Any\":\"name\""),
                                                meta,
                                                network))
                        .problems();

        assertEquals(
                List.of(
                        "the target network has no node column named 'missing', which Fixture input-columns always sends"),
                problems);
    }

    @Test
    void unknownNamesAreRejected() {
        assertEquals(
                List.of(
                        "parameters: unknown parameter 'Greting'; serviceapps describe lists the accepted names"),
                rejected("hello", params("\"Greting\":\"hi\"")));
        assertEquals(
                List.of(
                        "inputColumns: unknown input column 'Genes'; serviceapps describe lists the accepted names"),
                rejected("input-columns", columns("\"Genes\":\"name\"")));
        assertEquals(
                List.of(
                        "unknown field 'params'; serviceInput has only parameters and inputColumns"),
                rejected("hello", "{\"params\":{}}"));
    }

    @Test
    void everyProblemIsReported() {
        List<String> problems =
                rejected(
                        "input-columns",
                        "{\"parameters\":{\"Greeting\":{\"a\":1}},\"inputColumns\":{\"Nope\":\"x\"}}");

        assertEquals(2, problems.size(), problems.toString());
        assertTrue(
                problems.get(0).startsWith("Greeting: must be a single value"),
                problems.toString());
        assertTrue(problems.get(1).contains("unknown input column 'Nope'"), problems.toString());
    }

    @Test
    void anObjectPassedInsteadOfAStringIsExplained() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> parse("hello", "{parameters={Greeting=hi}}"));

        assertTrue(
                e.getMessage().startsWith("serviceInput must be passed as a JSON string"),
                e.getMessage());
    }
}
