package org.phylospec;

import org.phylospec.lexer.Range;

/**
 * @param description a short description of the error. Should end with a period
 * @param range the precise location causing the error
 * @param hint a short hint on ho to fix the error. Should be in imperative form targeted directly at the user and
 *             end with a period
 */
public record Error(
        String description,
        Range range,
        String hint
) {
}
