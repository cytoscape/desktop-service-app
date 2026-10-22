package org.cytoscape.serviceapps.form;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextRuleTest {

    @ParameterizedTest(name = "string ''{0}'' valid={1}")
    @CsvSource({"louvain,true", "leiden,true", "louvainx,false", "xleiden,false", "'',false"})
    void stringRegexMustMatchTheWholeText(String value, boolean valid) {
        TextRule rule =
                new TextRule("string", "louvain|leiden", null, null, "Must be louvain or leiden");

        assertEquals(valid, rule.validate(value).isEmpty(), value);
    }

    @ParameterizedTest(name = "number ''{0}'' valid={1}")
    @CsvSource({
        "0.5,true",
        "1,true",
        "0,true",
        "-0.0,true",
        ".25,true",
        "1.5,false",
        "-1,false",
        "abc,false",
        "'',false",
        "1e-1,true"
    })
    void numberAcceptsDecimalsWithinBounds(String value, boolean valid) {
        TextRule rule = new TextRule("number", null, 0.0, 1.0, null);

        assertEquals(valid, rule.validate(value).isEmpty(), value);
    }

    @ParameterizedTest(name = "digits ''{0}'' valid={1}")
    @CsvSource({
        "3,true",
        "-10,true",
        "10,true",
        "11,false",
        "-11,false",
        "2.5,false",
        "x,false",
        "'',false"
    })
    void digitsAcceptsWholeNumbersWithALeadingMinus(String value, boolean valid) {
        TextRule rule = new TextRule("digits", null, -10.0, 10.0, null);

        assertEquals(valid, rule.validate(value).isEmpty(), value);
    }

    @Test
    void theServiceMessageIsShownWhenGiven() {
        TextRule rule = new TextRule("digits", null, 0.0, 5.0, "Pick 0 to 5");

        assertEquals(Optional.of("Pick 0 to 5"), rule.validate("9"));
        assertEquals(
                Optional.of("Must be at most 5"),
                new TextRule("digits", null, 0.0, 5.0, null).validate("9"));
        assertEquals(
                Optional.of("Must be a number"),
                new TextRule("number", null, null, null, null).validate("x"));
    }

    @Test
    void noRegexAcceptsAnythingAndABadRegexDoesNotBlock() {
        assertTrue(new TextRule(null, null, null, null, null).validate("anything").isEmpty());
        assertTrue(new TextRule("string", "([", null, null, null).validate("x").isEmpty());
        assertTrue(new TextRule("unknown", null, null, null, null).validate("x").isEmpty());
    }
}
