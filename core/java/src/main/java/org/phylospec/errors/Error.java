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
                        .append(this.getMarkerPadding(sourceLine, range.start))
                        .append(markerColor)
                        .append(this.getMarkers(sourceLine, range.start, range.end))
                        .append(RESET);
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

                    text.append(this.getMarkerPadding(sourceLine, caretStart))
                            .append(markerColor)
                            .append(this.getMarkers(sourceLine, caretStart, caretEnd))
                            .append(RESET);
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
     * Returns the whitespace which moves the cursor beneath the given UTF-16 column of the source line.
     * Tabs are copied from the source line so that the markers end up at the same tab stops.
     */
    private String getMarkerPadding(String sourceLine, int column) {
        StringBuilder padding = new StringBuilder();

        int end = Math.min(column, sourceLine.length());
        for (int i = 0; i < end; ) {
            int codePoint = sourceLine.codePointAt(i);
            padding.append(codePoint == '\t' ? "\t" : " ".repeat(this.getDisplayWidth(codePoint)));
            i += Character.charCount(codePoint);
        }

        // columns past the end of the line (e.g. the line break) take up one cell each
        padding.append(" ".repeat(Math.max(0, column - sourceLine.length())));

        return padding.toString();
    }

    /**
     * Returns the markers underlining the source line between the given UTF-16 columns. At least one
     * marker is returned.
     */
    private String getMarkers(String sourceLine, int start, int end) {
        int width = 0;

        int clampedEnd = Math.min(end, sourceLine.length());
        for (int i = start; i < clampedEnd; ) {
            int codePoint = sourceLine.codePointAt(i);
            width += this.getDisplayWidth(codePoint);
            i += Character.charCount(codePoint);
        }

        // columns past the end of the line (e.g. the line break) take up one cell each
        width += Math.max(0, end - Math.max(start, sourceLine.length()));

        return "^".repeat(Math.max(1, width));
    }

    /**
     * Returns the number of terminal cells the given code point occupies: zero for combining marks and
     * invisible formatting characters, two for wide East Asian characters and emoji, and one otherwise.
     */
    private int getDisplayWidth(int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.FORMAT) {
            return 0;
        }

        boolean isWide = (codePoint >= 0x1100 && codePoint <= 0x115F) // hangul jamo
                || (codePoint >= 0x2E80 && codePoint <= 0x303E) // cjk radicals and punctuation
                || (codePoint >= 0x3041 && codePoint <= 0x33FF) // kana and cjk compatibility
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF) // cjk extension a
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF) // cjk unified ideographs
                || (codePoint >= 0xA000 && codePoint <= 0xA4CF) // yi
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3) // hangul syllables
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF) // cjk compatibility ideographs
                || (codePoint >= 0xFE30 && codePoint <= 0xFE4F) // cjk compatibility forms
                || (codePoint >= 0xFF00 && codePoint <= 0xFF60) // fullwidth forms
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6) // fullwidth signs
                || (codePoint >= 0x1F300 && codePoint <= 0x1F64F) // pictographs and emoticons
                || (codePoint >= 0x1F680 && codePoint <= 0x1F6FF) // transport and map symbols
                || (codePoint >= 0x1F900 && codePoint <= 0x1F9FF) // supplemental pictographs
                || (codePoint >= 0x20000 && codePoint <= 0x3FFFD); // cjk extensions b and beyond

        return isWide ? 2 : 1;
    }
}
