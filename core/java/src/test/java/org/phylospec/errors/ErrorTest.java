package org.phylospec.errors;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.phylospec.lexer.Range;

class ErrorTest {

    @Test
    void asciiMarkersAreUnchanged() {
        assertEquals("         ^", this.getMarkerLine("Real x = θ", new Range(1, 9, 10)));
        assertEquals("     ^^^^^", this.getMarkerLine("Real value = θ", new Range(1, 5, 10)));
    }

    @Test
    void supplementaryCharactersTakeUpOneCell() {
        // "𝛼" is two UTF-16 code units but is displayed in a single cell
        assertEquals("         ^", this.getMarkerLine("Real 𝛼 = θ", new Range(1, 10, 11)));
        assertEquals("     ^", this.getMarkerLine("Real 𝛼 = θ", new Range(1, 5, 7)));
    }

    @Test
    void combiningMarksTakeUpNoCell() {
        assertEquals("          ^", this.getMarkerLine("Real e\u0301x = θ", new Range(1, 11, 12)));
        assertEquals("     ^^", this.getMarkerLine("Real e\u0301x = θ", new Range(1, 5, 8)));
    }

    @Test
    void wideCharactersTakeUpTwoCells() {
        assertEquals("            ^", this.getMarkerLine("Real 人口 = θ", new Range(1, 10, 11)));
        assertEquals("     ^^^^", this.getMarkerLine("Real 人口 = θ", new Range(1, 5, 7)));
    }

    @Test
    void tabsAreCopiedIntoThePadding() {
        assertEquals("\t     ^", this.getMarkerLine("\tReal x", new Range(1, 6, 7)));
    }

    @Test
    void leadingByteOrderMarkTakesUpNoCell() {
        assertEquals("     ^", this.getMarkerLine("\uFEFFReal κ = 1.0", new Range(1, 6, 7)));
    }

    @Test
    void multilineMarkersUseDisplayWidths() {
        String output =
                this.stripColors(new Error(new Range(1, 2, 5, 1), "Problem.", "").toStdOutString("Real 人口\n𝛼 = 1"));
        String[] markerLines = Arrays.stream(output.split("\n"))
                .filter(line -> line.contains("^"))
                .map(line -> line.substring(line.indexOf(" | ") + 3))
                .toArray(String[]::new);

        assertArrayEquals(new String[] {"     ^^^^", "^"}, markerLines);
    }

    /**
     * Returns the marker line printed below a single-line source, without the gutter.
     */
    private String getMarkerLine(String source, Range range) {
        String output = this.stripColors(new Error(range, "Problem.", "").toStdOutString(source));
        String markerLine = Arrays.stream(output.split("\n"))
                .filter(line -> line.contains("^"))
                .findFirst()
                .orElseThrow();
        return markerLine.substring(markerLine.indexOf(" | ") + 3);
    }

    private String stripColors(String text) {
        return text.replaceAll("\033\\[[0-9;]*m", "");
    }
}
