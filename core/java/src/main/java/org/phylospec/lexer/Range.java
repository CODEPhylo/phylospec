package org.phylospec.lexer;

import java.util.Objects;

/** This class stores a range in the source code. */
public class Range {
    public final int startLine;
    public final int endLine;
    public final int start;
    public final int end;

    public Range(int line, int start, int end) {
        this.startLine = line;
        this.endLine = line;
        this.start = start;
        this.end = end;
    }

    public Range(int startLine, int endLine, int start, int end) {
        this.startLine = startLine;
        this.endLine = endLine;
        this.start = start;
        this.end = end;
    }

    public static Range combine(Range startRange, Range endRange) {
        return new Range(startRange.startLine, endRange.endLine, startRange.start, endRange.end);
    }

    public String toString() {
        if (startLine == endLine)
            return "(line " + startLine + " " + start + ":" + end +  ")";
        else
            return "(line " + startLine + " " + start + " : line " + endLine + " " + end +  ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Range that = (Range) o;
        return startLine == that.startLine && endLine == that.endLine && start == that.start && end == that.end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startLine, endLine, start, end);
    }
}
