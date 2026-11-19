package org.phylospec.errors;

import java.util.ArrayList;
import java.util.List;
import org.phylospec.lexer.Range;

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
    public Error(Range range, String description, String hint, List<String> examples) {
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
        return toStdOutString(source, false);
    }

    /**
     * Returns a formatted string with the highlighted problematic code which can be used
     * to print pretty error or warning messages on stdout.
     */
    public String toStdOutString(String source, boolean isWarning) {
        final String RESET = "\033[0m";
        final String BOLD = "\033[1m";
        final String RED = "\033[1;31m";
        final String YELLOW = "\033[1;33m";
        final String MUTED_YELLOW = "\033[33m";
        final String BLUE = "\033[1;34m";
        final String CYAN = "\033[36m";
        final String INDENT = "    ";
        final String headingColor = isWarning ? MUTED_YELLOW : RED;
        final String heading = isWarning ? "Please note:" : "There is a problem:";
        final String markerColor = isWarning ? MUTED_YELLOW : YELLOW;

        StringBuilder text = new StringBuilder();

        text.append(headingColor)
                .append(heading)
                .append(RESET)
                .append(" ")
                .append(isWarning ? description() : BOLD + description() + RESET);

        // the lexer skips a leading byte order mark, so the ranges do not account for it
        if (!source.isEmpty() && source.charAt(0) == 0xFEFF) {
            source = source.substring(1);
        }

        String[] lines = source.split("\n", -1);
        if (range != null && range.startLine >= 1 && range.startLine <= lines.length) {
            int lastLine = Math.min(range.endLine, lines.length);
            // gutter wide enough for the largest line number shown
            int gutterWidth = String.valueOf(lastLine).length();
            String gutter = " ".repeat(gutterWidth);

            if (range.startLine == range.endLine) {
                /* single-line range */
                String sourceLine = lines[range.startLine - 1];
                String lineLabel = String.format("%" + gutterWidth + "d", range.startLine);
                text.append("\n\n")
                        .append(INDENT)
                        .append(lineLabel)
                        .append(" | ")
                        .append(sourceLine)
                        .append("\n")
                        .append(INDENT)
                        .append(gutter)
                        .append(" | ")
                        .append(this.getMarkerLine(sourceLine, range.start, range.end, markerColor, RESET));
            } else {
                /* multiline range — show each line with carets beneath it */
                text.append("\n");
                for (int lineNum = range.startLine; lineNum <= lastLine; lineNum++) {
                    String sourceLine = lines[lineNum - 1];
                    String lineLabel = String.format("%" + gutterWidth + "d", lineNum);
                    text.append("\n")
                            .append(INDENT)
                            .append(lineLabel)
                            .append(" | ")
                            .append(sourceLine)
                            .append("\n")
                            .append(INDENT)
                            .append(gutter)
                            .append(" | ");

                    int caretStart, caretEnd;
                    if (lineNum == range.startLine) {
                        // first line: carets from range.start to end of line
                        caretStart = range.start;
                        caretEnd = sourceLine.length();
                    } else if (lineNum == range.endLine) {
                        // last line: carets from column 0 up to range.end
                        caretStart = 0;
                        caretEnd = range.end;
                    } else {
                        // middle lines: carets under the whole line
                        caretStart = 0;
                        caretEnd = sourceLine.length();
                    }

                    text.append(this.getMarkerLine(sourceLine, caretStart, caretEnd, markerColor, RESET));
                }
            }
        }

        if (!hint().isBlank()) {
            text.append("\n\n")
                    .append(BLUE)
                    .append("Hint:")
                    .append(RESET)
                    .append(" ")
                    .append(hint());
        }

        if (!examples().isEmpty()) {
            text.append("\n\n")
                    .append(BOLD)
                    .append("This could look like this:")
                    .append(RESET);
            for (String example : examples()) {
                text.append("\n\n").append(INDENT).append(CYAN).append(example).append(RESET);
            }
        }

        text.append("\n\n");

        return text.toString();
    }

    /* helper methods to align the markers with the source line */

    /**
     * Returns the markers underlining the source line between the given UTF-16 columns, preceded by
     * the whitespace which moves them beneath the marked text. At least one marker is returned.
     */
    private String getMarkerLine(String sourceLine, int start, int end, String markerColor, String resetColor) {
        start = Math.min(start, sourceLine.length());
        end = Math.max(start, Math.min(end, sourceLine.length()));

        String padding = this.mask(sourceLine.substring(0, start), " ");
        String markers = this.mask(sourceLine.substring(start, end), "^");

        return padding + markerColor + (markers.isBlank() ? "^" : markers) + resetColor;
    }

    /**
     * Returns the given text with every character replaced by the filler, repeated to match the
     * number of terminal cells the character occupies. Tabs are kept so that the result reaches the
     * same tab stops as the text.
     */
    private String mask(String text, String filler) {
        StringBuilder masked = new StringBuilder();
        text.codePoints()
                .forEach(codePoint ->
                        masked.append(codePoint == '\t' ? "\t" : filler.repeat(this.getDisplayWidth(codePoint))));
        return masked.toString();
    }

    /**
     * Returns the number of terminal cells the given code point occupies: zero for combining marks and
     * invisible formatting characters, two for East Asian scripts, and one otherwise.
     */
    private int getDisplayWidth(int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.FORMAT) {
            return 0;
        }

        return switch (Character.UnicodeScript.of(codePoint)) {
            case HAN, HIRAGANA, KATAKANA, HANGUL -> 2;
            default -> 1;
        };
    }
}
