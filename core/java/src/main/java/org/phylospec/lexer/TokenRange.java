package org.phylospec.lexer;

import java.util.Objects;

public class TokenRange {
    public final int startLine;
    public final int endLine;
    public final int start;
    public final int end;

    public TokenRange(int line, int start, int end) {
        this.startLine = line;
        this.endLine = line;
        this.start = start;
        this.end = end;
    }

    public TokenRange(int startLine, int endLine, int start, int end) {
        this.startLine = startLine;
        this.endLine = endLine;
        this.start = start;
        this.end = end;
    }

    public static TokenRange combine(TokenRange startRange, TokenRange endRange) {
        return new TokenRange(startRange.startLine, endRange.endLine, startRange.start, endRange.end);
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
        TokenRange that = (TokenRange) o;
        return startLine == that.startLine && endLine == that.endLine && start == that.start && end == that.end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startLine, endLine, start, end);
    }
}
