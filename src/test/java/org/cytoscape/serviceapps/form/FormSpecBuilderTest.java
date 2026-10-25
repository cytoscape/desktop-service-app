package org.cytoscape.serviceapps.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.serviceapps.fixture.ScenarioCatalog;
import org.cytoscape.serviceapps.model.ServiceMetadata;
import org.cytoscape.serviceapps.request.ServiceInput;
import org.cytoscape.serviceapps.testsupport.TestNetworks;
import org.cytoscape.serviceapps.types.CyColumnTypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every parameter type and attribute variant in the fixture catalogue becomes the right dialog
 * control, without a display.
 */
class FormSpecBuilderTest {

    private final FormSpecBuilder builder = new FormSpecBuilder();
    private final CyNetwork network = TestNetworks.withEveryColumnType();

    private FormSpec build(String scenario) {
        return builder.build(TestNetworks.scenario(scenario), new JsonObject(), network);
    }

    private static ControlSpec only(FormSpec spec) {
        assertEquals(1, spec.parameters().size());
        return spec.parameters().get(0);
    }

    @Test
    void textUsesTheLabelTooltipAndDefault() {
        ControlSpec text = only(build("param-text-string"));

        assertEquals(ControlKind.TEXT, text.kind());
        assertEquals("Algorithm", text.key());
        assertEquals("Algorithm", text.label());
        assertEquals("Help text for Algorithm", text.tooltip());
        assertEquals(List.of("louvain"), text.selected());
    }

    @Test
    void textValidationFollowsEachValidationType() {
        ControlSpec string = only(build("param-text-string"));
        assertTrue(string.validate(List.of("leiden")).isEmpty());
        assertEquals(
                "Must be louvain or leiden", string.validate(List.of("leidenx")).orElseThrow());

        ControlSpec number = only(build("param-text-number"));
        assertTrue(number.validate(List.of("0.75")).isEmpty());
        assertFalse(number.validate(List.of("abc")).isEmpty());
        assertFalse(number.validate(List.of("1.01")).isEmpty());

        ControlSpec digits = only(build("param-text-digits"));
        assertTrue(digits.validate(List.of("-10")).isEmpty());
        assertFalse(digits.validate(List.of("2.5")).isEmpty());
        assertFalse(digits.validate(List.of("11")).isEmpty());
    }

    @Test
    void textWithoutADefaultStartsEmpty() {
        assertEquals(List.of(""), only(build("param-text-no-default")).selected());
    }

    @Test
    void dropDownListsTheValuesInOrderWithTheDefaultSelected() {
        ControlSpec drop = only(build("param-dropdown"));

        assertEquals(ControlKind.DROP_DOWN, drop.kind());
        assertEquals(List.of("louvain", "leiden", "infomap"), drop.options());
        assertEquals(List.of("leiden"), drop.selected());
        assertTrue(only(build("param-dropdown-no-default")).selected().isEmpty());
    }

    @Test
    void radioSelectsTheDefaultOrNothing() {
        ControlSpec radio = only(build("param-radio"));

        assertEquals(ControlKind.RADIO, radio.kind());
        assertEquals(List.of("up", "down"), radio.options());
        assertEquals(List.of("down"), radio.selected());
        ControlSpec none = only(build("param-radio-no-default"));
        assertTrue(none.selected().isEmpty());
        assertTrue(none.validate(List.of()).orElseThrow().startsWith("Choose a value"));
    }

    @Test
    void checkBoxIsCheckedOnlyWhenTheDefaultIsTrue() {
        ControlSpec on = only(build("param-checkbox-true"));
        ControlSpec off = only(build("param-checkbox-false"));

        assertEquals(ControlKind.CHECK_BOX, on.kind());
        assertEquals(List.of("true"), on.selected());
        assertEquals(List.of("false"), off.selected());
        assertTrue(on.validate(List.of("false")).isEmpty());
        assertFalse(on.validate(List.of("yes")).isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"param-node-column-filters", "param-edge-column-filters"})
    void columnPickersListExactlyTheMatchingColumns(String scenario) {
        boolean nodes = scenario.contains("node");
        CyTable table = nodes ? network.getDefaultNodeTable() : network.getDefaultEdgeTable();
        FormSpec spec = build(scenario);

        assertEquals(ScenarioCatalog.COLUMN_TYPE_FILTERS.size(), spec.parameters().size());
        for (int i = 0; i < spec.parameters().size(); i++) {
            ControlSpec picker = spec.parameters().get(i);
            String filter = ScenarioCatalog.COLUMN_TYPE_FILTERS.get(i);
            assertEquals(nodes ? ControlKind.NODE_COLUMN : ControlKind.EDGE_COLUMN, picker.kind());

            List<String> expected = new ArrayList<>();
            for (CyColumn column : table.getColumns()) {
                if (!column.getName().equals("SUID")
                        && CyColumnTypes.matches(filter.isEmpty() ? null : filter, column)) {
                    expected.add(column.getName());
                }
            }
            assertEquals(
                    expected.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                    picker.options(),
                    "filter " + filter);
            String wrongPrefix = nodes ? "e_" : "c_";
            assertTrue(
                    picker.options().stream().noneMatch(o -> o.startsWith(wrongPrefix)),
                    "picker lists the other table's columns");
            if (!filter.isEmpty() && !filter.startsWith("list") && !filter.contains("number")) {
                assertTrue(picker.options().contains((nodes ? "c_" : "e_") + filter), filter);
            }
        }
    }

    @Test
    void aColumnPickerPreselectsTheDefaultWhenThatColumnExists() {
        ControlSpec stringPicker = build("param-node-column-filters").parameters().get(1);

        assertEquals(List.of("name"), stringPicker.selected());
    }

    @Test
    void withoutANetworkColumnPickersHaveNothingAndSayWhy() {
        FormSpec spec =
                builder.build(
                        TestNetworks.scenario("param-node-column-filters"), new JsonObject(), null);

        ControlSpec picker = spec.parameters().get(0);
        assertTrue(picker.options().isEmpty());
        assertTrue(picker.validate(List.of()).orElseThrow().contains("open one first"));
    }

    @Test
    void inputColumnsFollowTheirDefinitions() {
        FormSpec spec = build("input-columns");

        assertEquals(List.of("shared name"), spec.autoColumns());
        assertEquals(3, spec.inputColumns().size());
        ControlSpec gene = spec.inputColumns().get(0);
        assertEquals("Gene", gene.key());
        assertEquals(ControlKind.INPUT_COLUMN, gene.kind());
        assertFalse(gene.multiSelect());
        assertEquals(List.of("name"), gene.selected());
        assertTrue(gene.options().contains("c_string"));
        assertFalse(gene.options().contains("c_long"));

        ControlSpec scores = spec.inputColumns().get(1);
        assertTrue(scores.multiSelect());
        assertTrue(scores.options().containsAll(List.of("c_long", "c_integer", "c_double")));
        assertFalse(scores.options().contains("c_string"));
        assertTrue(scores.validate(List.of("c_long", "c_double")).isEmpty());

        ControlSpec any = spec.inputColumns().get(2);
        assertTrue(any.options().containsAll(TestNetworks.TYPED_COLUMNS));
    }

    @Test
    void edgeInputColumnsListEdgeColumns() {
        ControlSpec scores = build("input-edge-columns").inputColumns().get(1);

        assertTrue(scores.options().contains("e_double"));
        assertTrue(scores.options().stream().noneMatch(o -> o.startsWith("c_")));
    }

    @Test
    void theDescriptionIsShownOnlyWhenAsked() {
        assertEquals("A fixture service app for tests", build("description-shown").description());
        assertNull(build("description-hidden").description());
        FormSpec spec = build("hello");
        assertEquals("Hello World", spec.appName());
        assertEquals("1.0", spec.version());
        assertEquals("NA", spec.citation());
    }

    @Test
    void rememberedValuesOverrideDefaultsAndStaleOnesAreIgnored() {
        ServiceMetadata dropdown = TestNetworks.scenario("param-dropdown");
        JsonObject last =
                JsonParser.parseString("{\"parameters\":{\"Method\":\"infomap\",\"Gone\":\"x\"}}")
                        .getAsJsonObject();
        assertEquals(List.of("infomap"), only(builder.build(dropdown, last, network)).selected());

        JsonObject stale =
                JsonParser.parseString("{\"parameters\":{\"Method\":\"no-longer-offered\"}}")
                        .getAsJsonObject();
        assertEquals(List.of("leiden"), only(builder.build(dropdown, stale, network)).selected());

        JsonObject invalidText =
                JsonParser.parseString("{\"parameters\":{\"Resolution\":\"7\"}}").getAsJsonObject();
        assertEquals(
                List.of("0.5"),
                only(builder.build(
                                TestNetworks.scenario("param-text-number"), invalidText, network))
                        .selected());

        JsonObject columns =
                JsonParser.parseString("{\"inputColumns\":{\"Gene\":[\"c_string\"]}}")
                        .getAsJsonObject();
        assertEquals(
                List.of("c_string"),
                builder.build(TestNetworks.scenario("input-columns"), columns, network)
                        .inputColumns()
                        .get(0)
                        .selected());
    }

    @Test
    void unsupportedParameterTypesGetNoControl() {
        assertTrue(build("unsupported-file").parameters().isEmpty());
        assertTrue(build("unsupported-access-token").parameters().isEmpty());
        assertTrue(build("unsupported-ndex-uuid").parameters().isEmpty());
    }

    @Test
    void collectedValuesBecomeTheServiceInputAsText() {
        FormSpec spec = build("param-checkbox-true");
        ServiceInput input = spec.initialValues().toServiceInput();

        assertEquals(Map.of("Weighted", "true"), input.parameters());
        FormValues entered = new FormValues(Map.of("Weighted", List.of("false")), Map.of());
        assertEquals("false", entered.toServiceInput().parameters().get("Weighted"));
        assertEquals(entered, FormValues.fromJson(entered.toJson()));
    }

    @Test
    void aColumnPickerWithAListOfFiltersListsColumnsOfAnyOfThem() {
        ControlSpec picker = only(build("param-column-filter-list"));

        assertTrue(
                picker.options().containsAll(List.of("c_integer", "c_list_of_string", "c_long")),
                picker.options().toString());
        for (String option : picker.options()) {
            CyColumn column = network.getDefaultNodeTable().getColumn(option);
            assertTrue(
                    column.getType() == Long.class
                            || column.getType() == Integer.class
                            || column.getListElementType() == String.class,
                    option + " is " + column.getType());
        }
        ControlSpec empty =
                only(
                        builder.build(
                                TestNetworks.scenario("param-column-filter-list"),
                                new JsonObject(),
                                TestNetworks.empty()));
        assertEquals(
                Optional.of(
                        "The current network has no node column of type wholenumber or list_of_string"
                                + " for Score"),
                empty.validate(List.of()));
    }

    @Test
    void groupedParametersKeepTheirLabelsAndSharedNamesGetGroupKeys() {
        FormSpec spec = build("param-groups");

        assertEquals(
                List.of(
                        "Last name",
                        "First name",
                        "Top level",
                        "Office name",
                        "Nodes/Gap",
                        "Clusters/Gap"),
                spec.parameters().stream().map(ControlSpec::key).toList());
        ControlSpec nodesGap = spec.parameters().get(4);
        ControlSpec clustersGap = spec.parameters().get(5);
        assertEquals("Gap", nodesGap.label());
        assertEquals(List.of("Nodes"), nodesGap.groups());
        assertEquals(List.of("n"), nodesGap.selected());
        assertEquals(List.of("c"), clustersGap.selected());
        assertEquals(
                List.of("Company", "Department", "Office", "Identity"),
                spec.parameters().get(1).groups());
        assertEquals(List.of(), spec.parameters().get(2).groups());
        assertEquals(
                Map.of(
                        "Last name", "",
                        "First name", "",
                        "Top level", "",
                        "Office name", "",
                        "Nodes/Gap", "n",
                        "Clusters/Gap", "c"),
                spec.initialValues().toServiceInput().parameters());

        JsonObject remembered =
                JsonParser.parseString(
                                "{\"parameters\":{\"Clusters/Gap\":\"edited\",\"Gap\":\"x\"}}")
                        .getAsJsonObject();
        FormSpec again = builder.build(TestNetworks.scenario("param-groups"), remembered, network);
        assertEquals(List.of("n"), again.parameters().get(4).selected());
        assertEquals(List.of("edited"), again.parameters().get(5).selected());
    }

    @Test
    void ofParametersThatStillShareAKeyOnlyTheLastIsUsed() {
        ServiceMetadata meta = TestNetworks.scenario("param-groups");
        meta.parameters.get(5).groups = List.of("Nodes");
        meta.parameters.get(5).defaultValue = "last";

        FormSpec spec = builder.build(meta, new JsonObject(), network);

        assertEquals(
                1, spec.parameters().stream().filter(c -> c.key().equals("Nodes/Gap")).count());
        assertEquals(List.of("last"), spec.parameters().get(4).selected());
    }

    @Test
    void anAppWithInputTypeNoneGetsNoInputColumns() {
        ServiceMetadata meta = TestNetworks.scenario("input-columns");
        meta.serviceInputDefinition.type = "none";

        FormSpec spec = builder.build(meta, new JsonObject(), network);

        assertTrue(spec.inputColumns().isEmpty());
        assertTrue(spec.autoColumns().isEmpty());
    }

    @Test
    void anAppWithNoInputsStillGetsADialog() {
        FormSpec spec = builder.build(TestNetworks.scenario("input-none"), new JsonObject(), null);

        assertEquals("Fixture input-none", spec.appName());
        assertEquals(1, spec.parameters().size());
        assertTrue(spec.validate(spec.initialValues()).isEmpty());
    }
}
