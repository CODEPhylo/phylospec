package org.phylospec.ast;

import java.util.Set;

/**
 * Raised when an argument of a call cannot be bound to exactly one parameter of the
 * callee.
 */
public abstract sealed class ArgumentResolutionError extends RuntimeException {
    public final Expr.Argument argument;

    protected ArgumentResolutionError(Expr.Argument argument) {
        super("Argument resolution failed.");
        this.argument = argument;
    }

    public static final class MissingName extends ArgumentResolutionError {
        public MissingName(Expr.Argument argument) {
            super(argument);
        }
    }

    public static final class UnknownName extends ArgumentResolutionError {
        public final String name;
        public final Set<String> declaredNames;

        public UnknownName(Expr.Argument argument, String name, Set<String> declaredNames) {
            super(argument);
            this.name = name;
            this.declaredNames = declaredNames;
        }
    }

    public static final class DuplicateName extends ArgumentResolutionError {
        public final String name;

        public DuplicateName(Expr.Argument argument, String name) {
            super(argument);
            this.name = name;
        }
    }
}
