package org.phylospec.errors;

import org.phylospec.lexer.Range;

import java.util.ArrayList;
import java.util.List;

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
    public String toString() {
        StringBuilder text = new StringBuilder(description());

        if (!hint().isBlank()) {
            text.append("\n\n").append(hint());
        }

        if (!examples().isEmpty()) {
            text.append("\n\nFor example:\n");
            for (String example : examples()) {
                text.append("\n\t").append(example);
            }
        }

        return text.toString();
    }

    /**
     * Returns a formatted string with the highlighted problematic code which can be used
     * to print pretty error messages on stdout.
     */
    public String toStdOutString(String source) {
        final String RESET = "\033[0m";
        final String BOLD = "\033[1m";
        final String RED = "\033[1;31m";
        final String YELLOW = "\033[1;33m";
        final String BLUE = "\033[1;34m";
        final String CYAN = "\033[36m";
        final String INDENT = "    ";

        StringBuilder text = new StringBuilder();

        text.append(RED).append("There is a problem:").append(RESET)
                .append(" ").append(BOLD).append(description()).append(RESET);

        String[] lines = source.split("\n", -1);
        if (range != null && range.startLine >= 1 && range.startLine <= lines.length) {
            String sourceLine = lines[range.startLine - 1];
            text.append("\n\n").append(INDENT).append(sourceLine).append("\n").append(INDENT);

            // carets span from range.start to range.end on the start line
            int caretStart = range.start;
            int caretEnd = range.startLine == range.endLine ? range.end : sourceLine.length();
            int caretLen = Math.max(1, caretEnd - caretStart);

            text.append(" ".repeat(caretStart))
                    .append(YELLOW).append("^".repeat(caretLen)).append(RESET);
        }

        if (!hint().isBlank()) {
            text.append("\n\n").append(BLUE).append("Hint:").append(RESET).append(" ").append(hint());
        }

        if (!examples().isEmpty()) {
            text.append("\n\n").append(BOLD).append("This could look like this:").append(RESET);
            for (String example : examples()) {
                text.append("\n\n").append(INDENT).append(CYAN).append(example).append(RESET);
            }
        }

        text.append("\n\n");

        return text.toString();
    }

}
