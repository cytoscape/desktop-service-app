package org.cytoscape.serviceapps.model;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelParsingTest {

    /** The metadata shape from the spec's Service Metadata and parameters pages. */
    private static final String SPEC_METADATA =
            "{\"serviceInputDefinition\":{\"type\":\"node\",\"scope\":\"dynamic\","
                    + "\"inputColumns\":[{\"name\":\"Gene\",\"dataType\":\"string\","
                    + "\"allowMultipleSelection\":false,\"defaultColumnName\":\"name\"},"
                    + "{\"columnName\":\"shared name\"}]},"
                    + "\"cyWebActions\":[\"updateTables\"],"
                    + "\"cyWebMenuItem\":{\"root\":\"Apps\",\"path\":[{\"name\":\"Example\",\"gravity\":0},"
                    + "{\"name\":\"Service\",\"gravity\":1}]},"
                    + "\"name\":\"Example Service\",\"description\":\"An example\","
                    + "\"showDescriptionInDialog\":true,\"author\":\"A\",\"citation\":\"C\","
                    + "\"version\":\"1.0\",\"somethingNew\":{\"ignored\":true},"
                    + "\"parameters\":[{\"displayName\":\"Configuration Model\","
                    + "\"description\":\"Model\",\"type\":\"text\",\"valueList\":null,"
                    + "\"defaultValue\":\"Default\",\"validationType\":\"string\","
                    + "\"columnTypeFilter\":null,\"validationHelp\":\"Must be one of\","
                    + "\"validationRegex\":\"RB|RBER|CPM|Default\",\"minValue\":null,\"maxValue\":null},"
                    + "{\"displayName\":\"Weighted\",\"type\":\"checkBox\",\"defaultValue\":true},"
                    + "{\"displayName\":\"Resolution\",\"type\":\"text\",\"defaultValue\":0.5,"
                    + "\"minValue\":0,\"maxValue\":1}]}";

    @Test
    void parsesEverySpecField() {
        ServiceMetadata meta = Json.parse(SPEC_METADATA, ServiceMetadata.class);

        assertEquals("Example Service", meta.name);
        assertEquals("1.0", meta.version);
        assertEquals(Boolean.TRUE, meta.showDescriptionInDialog);
        assertEquals(List.of("updateTables"), meta.cyWebActions());
        assertEquals("Apps", meta.cyWebMenuItem.root);
        assertEquals("Service", meta.cyWebMenuItem.path().get(1).name);
        assertEquals(1.0, meta.cyWebMenuItem.path().get(1).gravity);
        assertEquals("node", meta.serviceInputDefinition.type);
        assertEquals("name", meta.serviceInputDefinition.inputColumns().get(0).defaultColumnName);
        assertEquals("shared name", meta.serviceInputDefinition.inputColumns().get(1).columnName);

        Parameter text = meta.parameters().get(0);
        assertEquals("Configuration Model", text.displayName);
        assertEquals("RB|RBER|CPM|Default", text.validationRegex);
        assertNull(text.minValue);
    }

    @Test
    void readsNonTextDefaultsAsText() {
        ServiceMetadata meta = Json.parse(SPEC_METADATA, ServiceMetadata.class);

        assertEquals("true", meta.parameters().get(1).defaultValue);
        assertEquals("0.5", meta.parameters().get(2).defaultValue);
        assertEquals(0.0, meta.parameters().get(2).minValue);
    }

    @Test
    void acceptsTheSingularCyWebActionSpelling() {
        ServiceMetadata meta =
                Json.parse(
                        "{\"name\":\"x\",\"version\":\"1\",\"cyWebAction\":[\"openURL\"]}",
                        ServiceMetadata.class);

        assertEquals(List.of("openURL"), meta.cyWebActions());
    }

    @Test
    void missingListsReadAsEmpty() {
        ServiceMetadata meta = Json.parse("{\"name\":\"x\"}", ServiceMetadata.class);

        assertTrue(meta.parameters().isEmpty());
        assertTrue(meta.cyWebActions().isEmpty());
    }

    @Test
    void readsTaskResultActionsInOrder() {
        TaskStatus status =
                Json.parse(
                        "{\"id\":\"t1\",\"status\":\"complete\",\"message\":\"\",\"progress\":100,"
                                + "\"result\":[{\"action\":\"updateSelection\",\"data\":{\"nodes\":[1]}},"
                                + "{\"action\":\"openURL\",\"data\":{\"url\":\"https://x\"}}]}",
                        TaskStatus.class);

        assertTrue(status.isComplete());
        assertFalse(status.isFailed());
        assertEquals(100.0, status.progress);
        List<ResultAction> actions = status.actions();
        assertEquals("updateSelection", actions.get(0).action);
        assertEquals("openURL", actions.get(1).action);
        assertEquals("https://x", actions.get(1).data.getAsJsonObject().get("url").getAsString());
    }

    @Test
    void aNullResultHasNoActions() {
        TaskStatus status =
                Json.parse("{\"status\":\"processing\",\"result\":null}", TaskStatus.class);

        assertTrue(status.actions().isEmpty());
    }

    @Test
    void aResultThatIsNotAListIsRejected() {
        TaskStatus status = Json.parse("{\"status\":\"complete\",\"result\":{}}", TaskStatus.class);

        assertThrows(JsonParseException.class, status::actions);
    }

    @Test
    void aResultEntryThatIsNotAnActionIsRejected() {
        for (String result :
                new String[] {
                    "[null,{\"action\":\"updateSelection\",\"data\":{}}]",
                    "[\"updateSelection\"]",
                    "[[1]]",
                    "[{\"data\":{}}]",
                    "[{\"action\":\" \",\"data\":{}}]"
                }) {
            TaskStatus status =
                    Json.parse(
                            "{\"status\":\"complete\",\"result\":" + result + "}",
                            TaskStatus.class);

            JsonParseException e = assertThrows(JsonParseException.class, status::actions, result);
            assertTrue(e.getMessage().startsWith("task result entry 1 "), e.getMessage());
        }
    }

    @Test
    void anEmptyBodyIsRejected() {
        assertThrows(JsonParseException.class, () -> Json.parse("", ServiceMetadata.class));
        assertThrows(JsonParseException.class, () -> Json.parse("null", ServiceMetadata.class));
    }

    @Test
    void writesTheTaskRequestShape() {
        TaskRequest request =
                new TaskRequest(
                        "Hello World",
                        com.google.gson.JsonParser.parseString("{\"a\":1}"),
                        java.util.Map.of("Greeting", "hi"));

        assertEquals(
                "{\"algorithm\":\"Hello World\",\"data\":{\"a\":1},\"parameters\":{\"Greeting\":\"hi\"}}",
                Json.write(request));
    }
}
