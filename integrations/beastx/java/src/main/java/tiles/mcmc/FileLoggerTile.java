package tiles.mcmc;

import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.tiling.tiles.TileInput;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class FileLoggerTile extends TemplateTile<Void, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Logger fileLogger = fileLogger(
                        logEvery=$logEvery,
                        file=$fileName,
                        parameters=$$parameters
                    )
                }""";
    }

    public TemplateTileInput<Integer, BeastXState> logEveryInput =
            new TemplateTileInput<>("$logEvery", Set.of(Stochasticity.CONSTANT));

    public TemplateTileInput<String, BeastXState> fileNameInput =
            new TemplateTileInput<>("$fileName", Set.of(Stochasticity.CONSTANT));

    public TileInput<LoggerParameterNames, BeastXState> parametersInput =
            new LoggerParameterNamesInput("$$parameters", false);

    @Override
    protected Void applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        int logEvery =
                this.logEveryInput.apply(beastState, indexVariables);

        if (logEvery <= 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "File logger frequency must be positive.",
                    "Use a positive integer, for example logEvery=1000."
            );
        }

        String fileName =
                this.fileNameInput.apply(beastState, indexVariables);

        LoggerParameterNames inputNames =
                this.parametersInput.apply(beastState, indexVariables);
        List<String> parameterNames =
                getLoggerNames("parameters");

        beastState.addFileLoggerSpec(
                logEvery,
                fileName,
                parameterNames != null ? parameterNames : inputNames == null ? null : inputNames.names
        );

        return null;
    }

    private List<String> getLoggerNames(String argumentName) {
        if (!(this.getRootNode() instanceof org.phylospec.ast.Stmt.Assignment assignment)
                || !(assignment.expression instanceof Expr.Call call)) {
            return null;
        }

        for (Expr.Argument argument : call.arguments) {
            if (argumentName.equals(argument.name)) {
                return getNames(argument.expression, argumentName);
            }
        }

        return null;
    }

    private List<String> getNames(Expr expression, String argumentName) {
        if (!(expression instanceof Expr.Array array)) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "File logger " + argumentName + " must be a list of names.",
                    "Use parameters=[clockRate] or parameters=[\"posterior\", \"prior\", \"likelihood\"]."
            );
        }

        List<String> names =
                new ArrayList<>();

        for (Expr element : array.elements) {
            if (element instanceof Expr.Variable variable) {
                names.add(variable.variableName);
                continue;
            }

            if (element instanceof Expr.Literal literal && literal.value instanceof String string) {
                names.add(string);
                continue;
            }

            throw new TileApplicationError(
                    this.getRootNode(),
                    "File logger " + argumentName + " must contain variable or string names.",
                    "Use parameters=[clockRate] or parameters=[\"posterior\", \"prior\", \"likelihood\"]."
            );
        }

        return names;
    }
}
