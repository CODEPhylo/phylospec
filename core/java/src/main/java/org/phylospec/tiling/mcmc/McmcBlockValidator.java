package org.phylospec.tiling.mcmc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.phylospec.ast.ArgumentResolutionError;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Expr.Call.Parameter;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.errors.TileApplicationError;

/// Validates the structure of the mcmc block for every engine.
///
/// The mcmc block is not type-checked. This validator makes sure that the block only declares
/// samplers and loggers, that their arguments are known, and that there is at most one sampler.
/// The values of the arguments are validated by the specs the engines build from the block.
public class McmcBlockValidator {

    private static final Map<String, List<Parameter>> SAMPLERS =
            Map.of("mcmc", List.of(new Parameter("chainLength", false)));

    private static final Map<String, List<Parameter>> LOGGERS = Map.of(
            "screenLogger",
            List.of(
                    new Parameter("logEvery", false),
                    new Parameter("parameters", false),
                    new Parameter("parameterNames", false)),
            "fileLogger",
            List.of(
                    new Parameter("file", true),
                    new Parameter("logEvery", false),
                    new Parameter("parameters", false),
                    new Parameter("parameterNames", false)),
            "treeLogger",
            List.of(new Parameter("file", true), new Parameter("tree", true), new Parameter("logEvery", false)));

    /// Settings which are not part of the mcmc block, mapped to a hint on where they belong.
    private static final Map<String, String> MISPLACED_SETTINGS = Map.of(
            "chainLength",
            "Declare a sampler instead, for example `Sampler sampler = mcmc(chainLength=1000000)`.",
            "defaultLogEvery",
            "Set the interval of each logger instead, for example `Logger screenLogger = screenLogger(logEvery=1000)`.",
            "randomSeed",
            "Set the seed on the command line with `--seed`.",
            "outputPrefix",
            "Set the output location on the command line with `--out` and `--run-name`.");

    /**
     * Validates all statements of the mcmc block among the given statements.
     * @throws TileApplicationError if the block is not valid
     */
    public static void validate(List<Stmt> statements) {
        boolean hasSampler = false;

        for (Stmt statement : statements) {
            if (!(statement.block instanceof Stmt.Block.Mcmc)) continue;

            Expr.Call call = getDeclaringCall(statement);
            validateArguments(call);

            if (!SAMPLERS.containsKey(call.functionName)) continue;

            if (hasSampler) {
                throw new TileApplicationError(
                        statement,
                        "The mcmc block declares more than one sampler.",
                        "Remove all but one sampler from the mcmc block.");
            }
            hasSampler = true;
        }
    }

    /**
     * Returns the call with which the given statement declares a sampler or a logger.
     * @throws TileApplicationError if the statement declares something else
     */
    private static Expr.Call getDeclaringCall(Stmt statement) {
        if (statement instanceof Stmt.Assignment assignment
                && assignment.expression instanceof Expr.Call call
                && getParameters(call.functionName) != null) {
            return call;
        }

        String hint = "Declare a sampler with `mcmc` or a logger with `screenLogger`, `fileLogger` or `treeLogger`."
                + " Settings of a single engine belong in the block of that engine.";
        if (statement instanceof Stmt.Assignment assignment && MISPLACED_SETTINGS.containsKey(assignment.name)) {
            hint = MISPLACED_SETTINGS.get(assignment.name);
        }

        throw new TileApplicationError(statement, "Only samplers and loggers can be declared in the mcmc block.", hint);
    }

    /**
     * Makes sure that every passed argument is known and that all required arguments are passed.
     * @throws TileApplicationError if the arguments cannot be bound to the parameters of the called function
     */
    private static void validateArguments(Expr.Call call) {
        List<Parameter> parameters = getParameters(call.functionName);

        try {
            call.resolveArgumentNames(parameters);
        } catch (ArgumentResolutionError.UnknownName error) {
            throw fail(
                    error.argument,
                    "`" + call.functionName + "` has no argument `" + error.name + "`.",
                    describeArguments(call.functionName));
        } catch (ArgumentResolutionError.MissingName error) {
            throw fail(
                    error.argument,
                    "An argument of `" + call.functionName + "` has no name.",
                    describeArguments(call.functionName));
        } catch (ArgumentResolutionError.DuplicateName error) {
            throw fail(
                    error.argument,
                    "The argument `" + error.name + "` of `" + call.functionName + "` is passed more than once.",
                    "Pass every argument only once.");
        } catch (ArgumentResolutionError.MissingRequired error) {
            throw fail(
                    call,
                    "`" + call.functionName + "` needs the argument `" + error.name + "`.",
                    describeArguments(call.functionName));
        }
    }

    private static TileApplicationError fail(AstNode node, String description, String hint) {
        return new TileApplicationError(node, description, hint);
    }

    private static String describeArguments(String functionName) {
        String argumentNames = getParameters(functionName).stream()
                .map(parameter -> "`" + parameter.name() + "`" + (parameter.required() ? " (required)" : ""))
                .collect(Collectors.joining(", "));
        return "The arguments of `" + functionName + "` are " + argumentNames + ".";
    }

    /**
     * Returns the parameters of the given sampler or logger function, or null if there is no such function.
     */
    private static List<Parameter> getParameters(String functionName) {
        Map<String, List<Parameter>> functions = new LinkedHashMap<>(SAMPLERS);
        functions.putAll(LOGGERS);
        return functions.get(functionName);
    }
}
