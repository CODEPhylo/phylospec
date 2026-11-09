package org.phylospec.ast;

import java.util.HashSet;
import java.util.Set;

/**
 * Raised when an argument of a call cannot be bound to exactly one parameter of the
 * callee.
 */
public abstract sealed class ArgumentResolutionError extends RuntimeException {

    protected ArgumentResolutionError() {
        super("Argument resolution failed.");
    }

    /** Raised when an argument is unnamed that cannot be unnamed. */
    public static final class MissingName extends ArgumentResolutionError {
        public final Expr.Argument argument;

        public MissingName(Expr.Argument argument) {
            this.argument = argument;
        }
    }

    /** Raised when an argument has an unknown name. */
    public static final class UnknownName extends ArgumentResolutionError {
        public final Expr.Argument argument;
        public final String name;
        public final Set<String> allowedNames;
        public final Set<String> usedNames;

        public UnknownName(Expr.Argument argument, String name, Set<String> allowedNames, Set<String> usedNames) {
            this.argument = argument;
            this.name = name;
            this.allowedNames = allowedNames;
            this.usedNames = usedNames;
        }

        /** Returns the allowed names which are not bound to any argument of the call. */
        public Set<String> getUnusedNames() {
            Set<String> unusedNames = new HashSet<>(this.allowedNames);
            unusedNames.removeAll(this.usedNames);
            return unusedNames;
        }
    }

    /** Raised when two arguments have the same name. */
    public static final class DuplicateName extends ArgumentResolutionError {
        public final Expr.Argument argument;
        public final String name;

        public DuplicateName(Expr.Argument argument, String name) {
            this.argument = argument;
            this.name = name;
        }
    }

    /** Raised when a required argument is missing. */
    public static final class MissingRequired extends ArgumentResolutionError {
        public final String name;

        public MissingRequired(String name) {
            this.name = name;
        }
    }
}
