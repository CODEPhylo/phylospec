package org.phylospec.tiling;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Unifies Dimension expressions during tile matching.
 *
 * A variable dimension can be bound once and then reused across later inputs.
 * For example:
 *
 * D unified with 4  -> D = 4
 * D unified with 4  -> match
 * D unified with 20 -> mismatch
 */
public class DimensionUnifier {

    public enum Result {
        MATCH,
        MISMATCH,
        UNKNOWN
    }

    private final Map<String, Dimension> bindings = new HashMap<>();

    public Result unify(Dimension expected, Dimension actual) {
        Dimension resolvedExpected = resolve(expected);
        Dimension resolvedActual = resolve(actual);

        if (resolvedExpected.isUnknown() || resolvedActual.isUnknown()) {
            return Result.UNKNOWN;
        }

        if (resolvedExpected instanceof Dimension.Variable variable) {
            return bind(variable, resolvedActual);
        }

        if (resolvedActual instanceof Dimension.Variable variable) {
            return bind(variable, resolvedExpected);
        }

        if (resolvedExpected.equals(resolvedActual)) {
            return Result.MATCH;
        }

        if (resolvedExpected instanceof Dimension.Literal
                && resolvedActual instanceof Dimension.Literal) {
            return Result.MISMATCH;
        }

        /*
         * Different symbolic expressions are not treated as a proven mismatch
         * yet. A later resolver can decide whether two symbolic expressions are
         * equivalent or incompatible.
         */
        return Result.UNKNOWN;
    }

    public Dimension resolve(Dimension dimension) {
        Dimension current = dimension;
        Set<String> seenVariables = new HashSet<>();

        while (current instanceof Dimension.Variable variable) {
            if (!seenVariables.add(variable.name())) {
                return Dimension.unknown();
            }

            Dimension bound = this.bindings.get(variable.name());

            if (bound == null) {
                return current;
            }

            current = bound;
        }

        return current;
    }

    public Map<String, Dimension> bindings() {
        return Map.copyOf(this.bindings);
    }

    private Result bind(Dimension.Variable variable, Dimension value) {
        if (value.isUnknown()) {
            return Result.UNKNOWN;
        }

        if (value.equals(variable)) {
            return Result.MATCH;
        }

        Dimension existing = this.bindings.get(variable.name());

        if (existing == null) {
            this.bindings.put(variable.name(), value);
            return Result.MATCH;
        }

        return unify(existing, value);
    }
}
