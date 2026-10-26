package org.cytoscape.serviceapps.command;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.JsonParser;

import org.cytoscape.work.Task;
import org.cytoscape.work.Tunable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code serviceapps} command carries the metadata Desktop's {@code help}, CyREST's command
 * swagger and the MCP Desktop app show.
 */
class CommandMetadataTest {

    static Stream<Class<? extends Task>> commands() {
        return Commands.ALL.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("commands")
    void theCommandHasCompleteMetadata(Class<? extends Task> type) {
        Commands.Command command = Commands.Command.of(type);

        assertFalse(command.name().isBlank());
        assertTrue(
                command.description().toLowerCase().contains("service app"),
                "the short description, which MCP search indexes, says what it's about: "
                        + command.description());
        assertTrue(command.longDescription().length() > command.description().length());
        assertTrue(
                JsonParser.parseString(command.exampleJson()).isJsonObject()
                        || JsonParser.parseString(command.exampleJson()).isJsonArray());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("commands")
    void everyArgumentIsDescribedWithAnExample(Class<? extends Task> type) {
        List<Field> arguments = new ArrayList<>();
        for (Field field : type.getFields()) {
            Tunable tunable = field.getAnnotation(Tunable.class);
            if (tunable == null) {
                continue;
            }
            arguments.add(field);
            assertFalse(tunable.description().isBlank(), field.getName());
            assertFalse(tunable.longDescription().isBlank(), field.getName());
            assertFalse(tunable.exampleStringValue().isBlank(), field.getName());
            assertEquals("nogui", tunable.context(), field.getName());
        }
        if (type != ListCommandTask.class) {
            assertFalse(arguments.isEmpty(), type.getSimpleName() + " takes arguments");
        }
    }

    @Test
    void theCommandNamesAreTheContract() {
        assertEquals(
                List.of(
                        "list",
                        "install",
                        "uninstall",
                        "status",
                        "describe",
                        "task call",
                        "task status"),
                commands().map(c -> Commands.Command.of(c).name()).toList());
    }

    @Test
    void listIsTheWayToDiscoverServiceAppsAndSaysWhatComesNext() {
        for (String text : List.of(ListCommandTask.DESCRIPTION, ListCommandTask.LONG_DESCRIPTION)) {
            assertTrue(
                    text.contains("not registered as") && text.contains("Desktop command"), text);
            assertTrue(text.contains("serviceapps describe"), text);
            assertTrue(text.contains("task call"), text);
        }
        assertTrue(ListCommandTask.DESCRIPTION.startsWith("Discover the service apps"));
        assertTrue(
                ListCommandTask.LONG_DESCRIPTION.contains("This command is how to discover them"));
        assertTrue(ListCommandTask.LONG_DESCRIPTION.contains("serviceapps task status"));
    }

    @Test
    void describeSaysWhereNamesComeFromAndDescribesOutputs() {
        for (String text :
                List.of(DescribeCommandTask.DESCRIPTION, DescribeCommandTask.LONG_DESCRIPTION)) {
            assertTrue(text.contains("serviceapps list"), text);
        }
        assertTrue(
                DescribeCommandTask.LONG_DESCRIPTION.contains(
                        "- resultActions: the app's outputs"));
    }

    /** The metadata is for any caller, so it names no particular client. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("commands")
    void theMetadataNamesNoParticularClient(Class<? extends Task> type) {
        Commands.Command command = Commands.Command.of(type);
        List<String> texts =
                new ArrayList<>(
                        List.of(
                                command.description(),
                                command.longDescription(),
                                command.exampleJson()));
        for (Field field : type.getFields()) {
            Tunable tunable = field.getAnnotation(Tunable.class);
            if (tunable != null) {
                texts.addAll(
                        List.of(
                                tunable.description(),
                                tunable.longDescription(),
                                tunable.exampleStringValue()));
            }
        }
        for (String text : texts) {
            for (String client : List.of("MCP", "Claude")) {
                assertFalse(text.contains(client), client + " in: " + text);
            }
        }
    }

    @Test
    void taskCallPointsAgentsToDescribeAndJsonStrings() {
        String text = TaskCallCommandTask.LONG_DESCRIPTION;

        assertTrue(text.contains("serviceapps list"), text);
        assertTrue(text.contains("serviceapps describe name="), text);
        assertTrue(text.contains("JSON *string*"), text);
        assertTrue(text.contains("serviceapps task status task_id="), text);
    }

    @Test
    void onlyTheArgumentsACommandCantDoWithoutAreRequired() {
        Map<String, Boolean> required = new TreeMap<>();
        for (Class<? extends Task> type : Commands.ALL) {
            for (Field field : type.getFields()) {
                Tunable tunable = field.getAnnotation(Tunable.class);
                if (tunable != null) {
                    required.put(
                            Commands.Command.of(type).name() + " " + field.getName(),
                            tunable.required());
                }
            }
        }

        assertEquals(
                new TreeMap<>(
                        Map.of(
                                "describe name", true,
                                "install name", false,
                                "install url", true,
                                "install version", false,
                                "status name", true,
                                "task call app", true,
                                "task call serviceInput", false,
                                "task call uiInput", false,
                                "task status task_id", true,
                                "uninstall name", true)),
                required);
    }
}
