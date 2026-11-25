package org.phylospec.tiling.mcmc;

import java.util.List;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.errors.TileApplicationError;

/// Rejects settings that earlier versions of PhyloSpec declared as plain values in the mcmc block.
/// Such values have no effect anymore, so they would silently fall back to the defaults.
public final class McmcBlockValidator {

    private static final Set<String> DECLARING_TYPES = Set.of("Sampler", "Logger");

    private McmcBlockValidator() {}

    /**
     * Validates the mcmc block among the given statements.
     * @throws TileApplicationError if the block declares one of the former settings
     */
    public static void validate(List<Stmt> statements) {
        for (Stmt statement : statements) {
            if (!(statement.block instanceof Stmt.Block.Mcmc)) continue;
            if (!(statement instanceof Stmt.Assignment assignment)) continue;
            if (assignment.type != null && DECLARING_TYPES.contains(assignment.type.name)) continue;

            String hint = getReplacementHint(assignment);
            if (hint != null) {
                throw new TileApplicationError(
                        assignment,
                        "`" + assignment.name + "` is no longer a setting of the mcmc block and would be ignored.",
                        hint);
            }
        }
    }

    /** Returns how to express the given former setting, or null if the assignment is no former setting. */
    private static String getReplacementHint(Stmt.Assignment assignment) {
        String value = getLiteralValue(assignment.expression);

        // earlier versions configured settings like chainLength with plain assignments in the mcmc block
        // (e.g. `chainLength = 1000000`). these hints point users to where each setting now lives
        return switch (assignment.name) {
            case "chainLength" -> "Set it on the sampler: `Sampler sampler = mcmc(chainLength=" + value + ")`.";
            case "defaultLogEvery" ->
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
