package org.phylospec.tiling.mcmc;

import java.util.List;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.errors.TileApplicationError;

/// Rejects mcmc blocks whose meaning would be ambiguous: a second sampler, and any statement other
/// than a sampler or logger declaration. Such statements have no effect, so for example a former
/// setting like `chainLength = 1000000` would silently fall back to the default.
public final class McmcBlockValidator {

    private static final Set<String> DECLARING_TYPES = Set.of("Sampler", "Logger");

    private McmcBlockValidator() {}

    /**
     * Validates the mcmc block among the given statements.
     * @throws TileApplicationError if the block declares more than one sampler or anything but samplers and loggers
     */
    public static void validate(List<Stmt> statements) {
        Stmt.Assignment sampler = null;
        for (Stmt statement : statements) {
            if (!(statement.block instanceof Stmt.Block.Mcmc)) continue;

            if (!isDeclaration(statement)) {
                throw getUnknownStatementError(statement);
            }

            Stmt.Assignment assignment = (Stmt.Assignment) statement;
            if (assignment.type.name.equals("Sampler")) {
                if (sampler != null) {
                    throw new TileApplicationError(
                            assignment,
                            "The mcmc block can only have one sampler, but `" + sampler.name + "` is already set.",
                            "Remove `" + assignment.name + "` or move its settings into `" + sampler.name + "`.");
                }
                sampler = assignment;
            }
        }
    }

    /** Returns whether the statement declares a sampler or a logger. */
    private static boolean isDeclaration(Stmt statement) {
        return statement instanceof Stmt.Assignment assignment
                && assignment.type != null
                && DECLARING_TYPES.contains(assignment.type.name);
    }

    private static TileApplicationError getUnknownStatementError(Stmt statement) {
        if (statement instanceof Stmt.Assignment assignment) {
            String hint = getReplacementHint(assignment);
            if (hint != null) {
                return new TileApplicationError(
                        assignment,
                        "`" + assignment.name + "` is not a setting of the mcmc block and would be ignored.",
                        hint);
            }
        }

        return new TileApplicationError(
                statement,
                "The mcmc block can only declare a sampler and loggers, so this statement would be ignored.",
                "Use `Sampler sampler = mcmc(...)` or `Logger log = fileLogger(...)`, or move the statement out of the mcmc block.");
    }

    /** Returns how to express the given former setting, or null if the assignment is no former setting. */
    private static String getReplacementHint(Stmt.Assignment assignment) {
        String value = getLiteralValue(assignment.expression);

        // earlier versions configured settings like chainLength with plain assignments in the mcmc block
        // (e.g. `chainLength = 1000000`). these hints point users to where each setting now lives
        return switch (assignment.name) {
            case "chainLength" -> "Set it on the sampler: `Sampler sampler = mcmc(chainLength=" + value + ")`.";
            case "defaultLogEvery", "logEvery" ->
                "Set `logEvery` on each logger, e.g. `Logger log = fileLogger(logEvery=" + value + ")`.";
            case "randomSeed" -> "Pass the seed on the command line: `--seed " + value + "`.";
            case "outputPrefix" -> "Pass the run name on the command line: `--run-name " + value + "`.";
            default -> null;
        };
    }

    private static String getLiteralValue(Expr expression) {
        if (expression instanceof Expr.Literal literal && literal.value != null) {
            return literal.value.toString();
        }
        return "...";
    }
}
