package org.phylospec.errors;

import org.phylospec.lexer.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 */
public final class Error extends Throwable {
    private final String description;
    private final Range range;
    private final String hint;
    private final List<String> examples;

    /**
     * @param range       the precise location causing the error
     * @param description a short description of the error. Should end with a period
     * @param hint        a short hint on ho to fix the error. Should be in imperative form targeted directly at the user and
     *                    end with a period
     */
    public Error(Range range, String description, String hint) {
        this.description = description;
        this.range = range;
        this.hint = hint;
        this.examples = new ArrayList<>();
    }

    /**
     * @param range       the precise location causing the error
     * @param description a short description of the error. Should end with a period
     * @param hint        a short hint on ho to fix the error. Should be in imperative form targeted directly at the user and
     *                    end with a period
     * @param examples    a list of example code snippets
     */
    public Error(
            Range range, String description,
            String hint,
            List<String> examples
    ) {
        this.description = description;
        this.range = range;
        this.hint = hint;
        this.examples = examples;
    }

    public String description() {
        return description;
    }

    public Range range() {
        return range;
    }

    public String hint() {
        return hint;
    }

    public List<String> examples() {
        return examples;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Error) obj;
        return Objects.equals(this.description, that.description) &&
                Objects.equals(this.range, that.range) &&
                Objects.equals(this.hint, that.hint) &&
                Objects.equals(this.examples, that.examples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, range, hint, examples);
    }

    @Override
    public String toString() {
        return "Error[" +
                "description=" + description + ", " +
                "range=" + range + ", " +
                "hint=" + hint + ", " +
                "examples=" + examples + ']';
    }

}
