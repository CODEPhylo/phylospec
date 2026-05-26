package tiles.mcmc;

import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.tiling.tiles.TilePriority;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.BeastXState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TreeLoggerTile extends Tile<Void, BeastXState> implements CandidateTile<BeastXState> {

    private final List<String> treeNames;
    private final List<Tile<?, BeastXState>> treeInputTiles;

    public TreeLoggerTile() {
        this.treeNames = null;
        this.treeInputTiles = List.of();
    }

    private TreeLoggerTile(
            List<String> treeNames,
            List<Tile<?, BeastXState>> treeInputTiles
    ) {
        this.treeNames = treeNames;
        this.treeInputTiles = treeInputTiles;
    }

    @Override
    public Set<Tile<?, BeastXState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BeastXState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt {
        if (!(node instanceof Stmt.Assignment assignment)) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        if (!assignment.name.equals("treeLogger")) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        if (!(assignment.expression instanceof Expr.Call call)) {
            throw new FailedTilingAttempt.Rejected(
                    "treeLogger must be created by calling treeLogger(...)."
            );
        }

        if (!call.functionName.equals("treeLogger")) {
            throw new FailedTilingAttempt.Rejected(
                    "treeLogger must be created by calling treeLogger(...)."
            );
        }

        List<Expr.Variable> treeVariables =
                getTreeVariables(assignment);

        List<String> treeNames =
                treeVariables.stream()
                        .map(variable -> variable.variableName)
                        .toList();

        List<Set<Tile<?, BeastXState>>> possibleTreeTiles =
                new ArrayList<>();

        for (Expr.Variable treeVariable : treeVariables) {
            Set<Tile<?, BeastXState>> tiles =
                    allInputTiles.get(treeVariable);

            if (tiles == null || tiles.isEmpty()) {
                throw new FailedTilingAttempt.RejectedCascade(treeVariable);
            }

            possibleTreeTiles.add(tiles);
        }

        Set<Tile<?, BeastXState>> treeLoggerTiles =
                new HashSet<>();

        Utils.visitCombinations(
                possibleTreeTiles,
                selectedTreeTiles -> {
                    TreeLoggerTile tile =
                            new TreeLoggerTile(
                                    treeNames,
                                    new ArrayList<>(selectedTreeTiles)
                            );

                    tile.setRootNode(node);

                    int inputWeight =
                            selectedTreeTiles.stream()
                                    .mapToInt(Tile::getWeight)
                                    .sum();

                    tile.setWeight(getPriority().getWeight() + inputWeight);

                    treeLoggerTiles.add(tile);
                }
        );

        return treeLoggerTiles;
    }

    @Override
    protected Void applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Stmt.Assignment assignment =
                (Stmt.Assignment) this.getRootNode();

        int logEvery =
                getLogEvery(assignment);

        if (logEvery <= 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Tree logger frequency must be positive.",
                    "Use a positive integer, for example logEvery=1000."
            );
        }

        String fileName =
                getFileName(assignment);

        for (Tile<?, BeastXState> treeInputTile : this.treeInputTiles) {
            treeInputTile.apply(beastState, indexVariables);
        }

        beastState.addTreeLoggerSpec(logEvery, fileName, this.treeNames);
        return null;
    }

    private static int getLogEvery(Stmt.Assignment assignment) {
        Expr.Call call =
                (Expr.Call) assignment.expression;

        for (Expr.Argument argument : call.arguments) {
            if (argument.name == null || argument.name.equals("logEvery")) {
                return getIntegerLiteral(argument.expression, assignment);
            }
        }

        throw new TileApplicationError(
                assignment,
                "treeLogger requires a logEvery argument.",
                "Use Logger treeLogger = treeLogger(logEvery=1000, file=\"trees.log\", trees=[tree])."
        );
    }

    private static String getFileName(Stmt.Assignment assignment) {
        Expr.Call call =
                (Expr.Call) assignment.expression;

        for (Expr.Argument argument : call.arguments) {
            if (argument.name != null && argument.name.equals("file")) {
                return getStringLiteral(argument.expression, assignment);
            }
        }

        throw new TileApplicationError(
                assignment,
                "treeLogger requires a file argument.",
                "Use Logger treeLogger = treeLogger(logEvery=1000, file=\"trees.log\", trees=[tree])."
        );
    }

    private static List<Expr.Variable> getTreeVariables(Stmt.Assignment assignment) {
        Expr.Call call =
                (Expr.Call) assignment.expression;

        for (Expr.Argument argument : call.arguments) {
            if (argument.name != null && argument.name.equals("trees")) {
                return getVariableList(argument.expression, assignment);
            }
        }

        throw new TileApplicationError(
                assignment,
                "treeLogger requires a trees argument.",
                "Use Logger treeLogger = treeLogger(logEvery=1000, file=\"trees.log\", trees=[tree])."
        );
    }

    private static List<Expr.Variable> getVariableList(Expr expression, Stmt.Assignment assignment) {
        if (!(expression instanceof Expr.Array array)) {
            throw new TileApplicationError(
                    assignment,
                    "treeLogger trees must be a list of tree names.",
                    "Use trees=[tree]."
            );
        }

        List<Expr.Variable> variables =
                new ArrayList<>();

        for (Expr element : array.elements) {
            if (!(element instanceof Expr.Variable variable)) {
                throw new TileApplicationError(
                        assignment,
                        "treeLogger trees must be a list of tree names.",
                        "Use trees=[tree]."
                );
            }

            variables.add(variable);
        }

        return variables;
    }

    private static int getIntegerLiteral(Expr expression, Stmt.Assignment assignment) {
        if (!(expression instanceof Expr.Literal literal)) {
            throw new TileApplicationError(
                    assignment,
                    "treeLogger logEvery must be a constant integer.",
                    "Use a literal value, for example logEvery=1000."
            );
        }

        if (!(literal.value instanceof Number number)) {
            throw new TileApplicationError(
                    assignment,
                    "treeLogger logEvery must be a constant integer.",
                    "Use a literal value, for example logEvery=1000."
            );
        }

        double value =
                number.doubleValue();

        if (value != Math.rint(value)) {
            throw new TileApplicationError(
                    assignment,
                    "treeLogger logEvery must be an integer.",
                    "Use a whole number, for example logEvery=1000."
            );
        }

        return (int) value;
    }

    private static String getStringLiteral(Expr expression, Stmt.Assignment assignment) {
        if (!(expression instanceof Expr.Literal literal)) {
            throw new TileApplicationError(
                    assignment,
                    "treeLogger file must be a constant string.",
                    "Use file=\"trees.log\"."
            );
        }

        if (!(literal.value instanceof String fileName)) {
            throw new TileApplicationError(
                    assignment,
                    "treeLogger file must be a constant string.",
                    "Use file=\"trees.log\"."
            );
        }

        return fileName;
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.CUSTOM;
    }
}