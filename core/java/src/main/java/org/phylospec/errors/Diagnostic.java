package org.phylospec.errors;

import java.util.List;
import org.phylospec.lexer.Range;

/**
 * A single problem found in a PhyloSpec script, with its location, in a form
 * that is independent of any particular output (terminal, JSON, LSP).
 *
 * <p>Lines and columns are 1-based. {@code endColumn} is inclusive, so a
 * diagnostic covering a single character has {@code column == endColumn}. The
 * location fields are {@code null} if the error has no known position.
 *
 * @param severity  whether this is an error or a warning
 * @param message   a short description of the problem
 * @param hint      how to fix it; empty if there is no hint
 * @param examples  example code snippets illustrating the fix; may be empty
 * @param line      the first line of the problematic code
 * @param column    the first column of the problematic code
 * @param endLine   the last line of the problematic code
 * @param endColumn the last column of the problematic code
 * @param error     the underlying error, used for pretty-printing
 */
public record Diagnostic(
        Severity severity,
        String message,
        String hint,
        List<String> examples,
        Integer line,
        Integer column,
        Integer endLine,
        Integer endColumn,
        Error error) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public static Diagnostic of(Error error, Severity severity) {
        Range range = error.range();
        String hint = error.hint() == null ? "" : error.hint();
        List<String> examples = error.examples() == null ? List.of() : List.copyOf(error.examples());

        if (range == null) {
            return new Diagnostic(severity, error.description(), hint, examples, null, null, null, null, error);
        }

        // Range columns are 0-based with an exclusive end, which is the same
        // position as a 1-based inclusive end
        return new Diagnostic(
                severity,
                error.description(),
                hint,
                examples,
                range.startLine,
                range.start + 1,
                range.endLine,
                Math.max(range.end, range.start + 1),
                error);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
