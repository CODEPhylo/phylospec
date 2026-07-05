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

public class TreeLoggerTile extends TemplateTile<Void, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                mcmc {
                    Logger treeLogger = treeLogger(
                        logEvery=$logEvery,
                        file=$fileName,
                        trees=$$trees
                    )
                }""";
    }

    public TemplateTileInput<Integer, BeastXState> logEveryInput =
            new TemplateTileInput<>("$logEvery", Set.of(Stochasticity.CONSTANT));

    public TemplateTileInput<String, BeastXState> fileNameInput =
            new TemplateTileInput<>("$fileName", Set.of(Stochasticity.CONSTANT));

    public TileInput<LoggerTreeNames, BeastXState> treesInput =
            new LoggerTreeNamesInput("$$trees", false);

    @Override
    protected Void applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        int logEvery =
                this.logEveryInput.apply(beastState, indexVariables);

        if (logEvery <= 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Tree logger frequency must be positive.",
                    "Use a positive integer, for example logEvery=1000."
            );
        }

        String fileName =
                this.fileNameInput.apply(beastState, indexVariables);

        LoggerTreeNames inputNames =
                this.treesInput.apply(beastState, indexVariables);
        List<String> treeNames =
                getLoggerNames("trees");

        beastState.addTreeLoggerSpec(
                logEvery,
                fileName,
                treeNames != null ? treeNames : inputNames == null ? null : inputNames.names
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
                    "Tree logger " + argumentName + " must be a list of tree names.",
                    "Use trees=[tree]."
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
                    "Tree logger " + argumentName + " must contain tree names.",
                    "Use trees=[tree]."
            );
        }

        return names;
    }
}
