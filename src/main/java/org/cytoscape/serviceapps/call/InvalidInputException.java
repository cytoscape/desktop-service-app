package org.cytoscape.serviceapps.call;

import java.util.List;

/** {@code serviceInput} or {@code uiInput} was rejected; every problem names its field. */
public class InvalidInputException extends IllegalArgumentException {

    private final List<String> problems;

    public InvalidInputException(String argument, List<String> problems) {
        super(argument + " was rejected: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
