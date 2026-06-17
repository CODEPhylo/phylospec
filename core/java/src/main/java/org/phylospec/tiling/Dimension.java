package org.phylospec.tiling;

import java.util.OptionalLong;

/**
 * Represents a one-dimensional size expression used during tiling.
 *
 * Literal dimensions cover the current fixed-size use cases, such as
 * Simplex(4) for nucleotide base frequencies and Simplex(20) for amino-acid
 * base frequencies.
 *
 * Symbolic dimensions are used for expressions such as numStates(alignment).
 * Variables are used by the tiler to unify dimensions across multiple inputs,
 * for example requiring two inputs to both have dimension D.
 */
public sealed interface Dimension
        permits Dimension.Literal, Dimension.Symbolic, Dimension.Variable, Dimension.Unknown {

    static Dimension literal(long value) {
        return new Literal(value);
    }

    static Dimension symbolic(String expression) {
        return new Symbolic(expression);
    }

    static Dimension variable(String name) {
        return new Variable(name);
    }

    static Dimension unknown() {
        return new Unknown();
    }

    OptionalLong literalValue();

    String display();

    default boolean isUnknown() {
        return this instanceof Unknown;
    }

    record Literal(long value) implements Dimension {
        public Literal {
            if (value < 0) {
                throw new IllegalArgumentException("Dimension literal must be non-negative.");
            }
        }

        @Override
        public OptionalLong literalValue() {
            return OptionalLong.of(value);
        }

        @Override
        public String display() {
            return Long.toString(value);
        }
    }

    record Symbolic(String expression) implements Dimension {
        public Symbolic {
            if (expression == null || expression.isBlank()) {
                throw new IllegalArgumentException("Symbolic dimension expression must not be blank.");
            }
        }

        @Override
        public OptionalLong literalValue() {
            return OptionalLong.empty();
        }

        @Override
        public String display() {
            return expression;
        }
    }

    record Variable(String name) implements Dimension {
        public Variable {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Dimension variable name must not be blank.");
            }
        }

        @Override
        public OptionalLong literalValue() {
            return OptionalLong.empty();
        }

        @Override
        public String display() {
            return name;
        }
    }

    record Unknown() implements Dimension {
        @Override
        public OptionalLong literalValue() {
            return OptionalLong.empty();
        }

        @Override
        public String display() {
            return "unknown";
        }
    }
}