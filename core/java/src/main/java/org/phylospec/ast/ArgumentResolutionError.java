package org.phylospec.ast;

import java.util.Set;

/**
 * Raised when an argument of a call cannot be bound to exactly one parameter of the
 * callee.
 */
public abstract sealed class ArgumentResolutionError extends RuntimeException {

    protected ArgumentResolutionError() {
        super("Argument resolution failed.");
    }

    public static final class MissingName extends ArgumentResolutionError {
        public final Expr.Argument argument;

        public MissingName(Expr.Argument argument) {
            this.argument = argument;
        }
    }

    public static final class UnknownName extends ArgumentResolutionError {
        public final Expr.Argument argument;
        public final String name;
        public final Set<String> allowedNames;

        public UnknownName(Expr.Argument argument, String name, Set<String> allowedNames) {
            this.argument = argument;
            this.name = name;
            this.allowedNames = allowedNames;
        }
    }

    public static final class DuplicateName extends ArgumentResolutionError {
        public final Expr.Argument argument;
        public final String name;

        public DuplicateName(Expr.Argument argument, String name) {
            this.argument = argument;
            this.name = name;
        }
    }

    public static final class MissingRequired extends ArgumentResolutionError {
        public final String name;

        public MissingRequired(String name) {
            this.name = name;
        }
    }
}
