package org.phylospec.lexer;

import java.util.Objects;

public class TokenRange {
    public final int line;
    public final int start;
    public final int end;

    public TokenRange(int line, int start, int end) {
        this.line = line;
        this.start = start;
        this.end = end;
    }

    public String toString() {
        return "(line " + line + " " + start + ":" + end +  ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TokenRange that = (TokenRange) o;
        return line == that.line && start == that.start && end == that.end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(line, start, end);
    }
}
