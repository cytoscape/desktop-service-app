package org.cytoscape.serviceapps.call;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import org.cytoscape.serviceapps.model.ResultAction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code describe}'s outputs: one effect for each result action the spec defines. */
class ResultActionEffectsTest {

    @Test
    void everySpecActionHasAnEffectAndNothingElseDoes() {
        assertEquals(
                ResultAction.ALL.stream().sorted().toList(),
                ResultActionEffects.EFFECTS.keySet().stream().sorted().toList());
        ResultActionEffects.EFFECTS.forEach(
                (action, effect) -> {
                    assertFalse(effect.text().isBlank(), action);
                    assertTrue(effect.text().endsWith("."), action);
                });
    }

    /** The user manual's result table lists the same actions, so the two can't drift apart. */
    @Test
    void theManualsResultTableListsTheSameActions() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("docs/Manual.md"));
        int header = lines.indexOf("| Action | What happens in Desktop |");
        assertTrue(header >= 0, "the Manual's result table");
        List<String> rows = new ArrayList<>();
        for (String line : lines.subList(header + 2, lines.size())) {
            if (!line.startsWith("|")) {
                break;
            }
            rows.add(line.split("\\|")[1].strip());
        }
        List<String> actions = new ArrayList<>();
        for (String action : ResultActionEffects.EFFECTS.keySet()) {
            // updateSelection -> "Update selection", openURL -> "Open URL"
            String words = action.replaceAll("([a-z])([A-Z])", "$1 $2");
            actions.add(
                    Character.toUpperCase(words.charAt(0))
                            + (words.endsWith("URL")
                                    ? words.substring(1)
                                    : words.substring(1).toLowerCase(Locale.ROOT)));
        }
        assertEquals(actions.stream().sorted().toList(), rows.stream().sorted().toList());
    }

    @Test
    void networkActionsTargetANetworkAndLayoutsAlsoAView() {
        for (String action :
                List.of(
                        ResultAction.UPDATE_SELECTION,
                        ResultAction.UPDATE_TABLES,
                        ResultAction.UPDATE_NETWORK,
                        ResultAction.ADD_TABLES)) {
            assertEquals(
                    List.of(UiTargetResolver.NETWORK_ID),
                    ResultActionEffects.EFFECTS.get(action).targets(),
                    action);
        }
        assertEquals(
                List.of(UiTargetResolver.NETWORK_ID, UiTargetResolver.NETWORK_VIEW_ID),
                ResultActionEffects.EFFECTS.get(ResultAction.UPDATE_LAYOUTS).targets());
        assertEquals(
                List.of(), ResultActionEffects.EFFECTS.get(ResultAction.ADD_NETWORKS).targets());
        assertEquals(List.of(), ResultActionEffects.EFFECTS.get(ResultAction.OPEN_URL).targets());
    }
}
