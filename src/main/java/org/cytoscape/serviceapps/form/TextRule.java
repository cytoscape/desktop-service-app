package org.cytoscape.serviceapps.form;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The validation rules for a {@code text} parameter.
 *
 * @param type {@code string}, {@code number} or {@code digits}; anything else is {@code string}
 * @param regex for {@code string}: the whole text must match; null for none
 * @param min for {@code number} and {@code digits}: the lowest value allowed, or null
 * @param max for {@code number} and {@code digits}: the highest value allowed, or null
 * @param help the service's message for an invalid value, or null
 * @see <a href="https://github.com/cytoscape/cytoscape-web/wiki/parameters">parameters</a>
 */
public record TextRule(String type, String regex, Double min, Double max, String help) {

    public static final String STRING = "string";
    public static final String NUMBER = "number";
    public static final String DIGITS = "digits";

    private static final Pattern DECIMAL =
            Pattern.compile("-?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");
    private static final Pattern WHOLE = Pattern.compile("-?\\d+");

    public TextRule {
        type = type == null ? STRING : type.strip().toLowerCase(Locale.ROOT);
        if (!type.equals(NUMBER) && !type.equals(DIGITS)) {
            type = STRING;
        }
    }

    /** Why {@code value} is invalid, or empty if it is valid. */
    public Optional<String> validate(String value) {
        String text = value == null ? "" : value.strip();
        switch (type) {
            case NUMBER:
                if (!DECIMAL.matcher(text).matches()) {
                    return fail("Must be a number");
                }
                return inRange(Double.parseDouble(text));
            case DIGITS:
                if (!WHOLE.matcher(text).matches()) {
                    return fail("Must be a whole number");
                }
                return inRange(Double.parseDouble(text));
            default:
                if (regex == null || regex.isEmpty()) {
                    return Optional.empty();
                }
                try {
                    return Pattern.compile(regex).matcher(value == null ? "" : value).matches()
                            ? Optional.empty()
                            : fail("Must match " + regex);
                } catch (PatternSyntaxException e) {
                    // An invalid pattern from the service can't be checked; don't block the user.
                    return Optional.empty();
                }
        }
    }

    private Optional<String> inRange(double number) {
        if (min != null && number < min) {
            return fail("Must be at least " + format(min));
        }
        if (max != null && number > max) {
            return fail("Must be at most " + format(max));
        }
        return Optional.empty();
    }

    private Optional<String> fail(String fallback) {
        return Optional.of(help == null || help.isBlank() ? fallback : help);
    }

    private static String format(double number) {
        return number == Math.rint(number) ? Long.toString((long) number) : Double.toString(number);
    }
}
