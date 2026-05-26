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

public class FileLoggerTile extends Tile<Void, BeastXState> implements CandidateTile<BeastXState> {

    private final List<String> parameterNames;
    private final List<Tile<?, BeastXState>> parameterInputTiles;

    public FileLoggerTile() {
        this.parameterNames = null;
        this.parameterInputTiles = List.of();
    }

    private FileLoggerTile(
            List<String> parameterNames,
            List<Tile<?, BeastXState>> parameterInputTiles
    ) {
        this.parameterNames = parameterNames;
        this.parameterInputTiles = parameterInputTiles;
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

        if (!assignment.name.equals("fileLogger")) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        if (!(assignment.expression instanceof Expr.Call call)) {
            throw new FailedTilingAttempt.Rejected(
                    "fileLogger must be created by calling fileLogger(...)."
            );
        }

        if (!call.functionName.equals("fileLogger")) {
            throw new FailedTilingAttempt.Rejected(
                    "fileLogger must be created by calling fileLogger(...)."
            );
        }

        List<Expr.Variable> parameterVariables =
                getParameterVariables(assignment);

        if (parameterVariables == null) {
            FileLoggerTile tile =
                    new FileLoggerTile(null, List.of());

            tile.setRootNode(node);
            tile.setWeight(getPriority().getWeight());

            return Set.of(tile);
        }

        List<String> parameterNames =
                parameterVariables.stream()
                        .map(variable -> variable.variableName)
                        .toList();

        List<Set<Tile<?, BeastXState>>> possibleParameterTiles =
                new ArrayList<>();

        for (Expr.Variable parameterVariable : parameterVariables) {
            Set<Tile<?, BeastXState>> tiles =
                    allInputTiles.get(parameterVariable);

            if (tiles == null || tiles.isEmpty()) {
                throw new FailedTilingAttempt.RejectedCascade(parameterVariable);
            }

            possibleParameterTiles.add(tiles);
        }

        Set<Tile<?, BeastXState>> fileLoggerTiles =
                new HashSet<>();

        Utils.visitCombinations(
                possibleParameterTiles,
                selectedParameterTiles -> {
                    FileLoggerTile tile =
                            new FileLoggerTile(
                                    parameterNames,
                                    new ArrayList<>(selectedParameterTiles)
                            );

                    tile.setRootNode(node);

                    int inputWeight =
                            selectedParameterTiles.stream()
                                    .mapToInt(Tile::getWeight)
                                    .sum();

                    tile.setWeight(getPriority().getWeight() + inputWeight);

                    fileLoggerTiles.add(tile);
                }
        );

        return fileLoggerTiles;
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
                    "File logger frequency must be positive.",
                    "Use a positive integer, for example logEvery=1000."
            );
        }

        String fileName =
                getFileName(assignment);

        for (Tile<?, BeastXState> parameterInputTile : this.parameterInputTiles) {
            parameterInputTile.apply(beastState, indexVariables);
        }

        beastState.addFileLoggerSpec(logEvery, fileName, this.parameterNames);
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
                "fileLogger requires a logEvery argument.",
                "Use Logger fileLogger = fileLogger(logEvery=1000, file=\"output.log\")."
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
                "fileLogger requires a file argument.",
                "Use Logger fileLogger = fileLogger(logEvery=1000, file=\"output.log\")."
        );
    }

    private static List<Expr.Variable> getParameterVariables(Stmt.Assignment assignment) {
        Expr.Call call =
                (Expr.Call) assignment.expression;

        for (Expr.Argument argument : call.arguments) {
            if (argument.name != null && argument.name.equals("parameters")) {
                return getVariableList(argument.expression, assignment);
            }
        }

        return null;
    }

    private static List<Expr.Variable> getVariableList(Expr expression, Stmt.Assignment assignment) {
        if (!(expression instanceof Expr.Array array)) {
            throw new TileApplicationError(
                    assignment,
                    "fileLogger parameters must be a list of parameter names.",
                    "Use parameters=[kappa, clockRate]."
            );
        }

        List<Expr.Variable> variables =
                new ArrayList<>();

        for (Expr element : array.elements) {
            if (!(element instanceof Expr.Variable variable)) {
                throw new TileApplicationError(
                        assignment,
                        "fileLogger parameters must be a list of parameter names.",
                        "Use parameters=[kappa, clockRate]."
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
                    "fileLogger logEvery must be a constant integer.",
                    "Use a literal value, for example logEvery=1000."
            );
        }

        if (!(literal.value instanceof Number number)) {
            throw new TileApplicationError(
                    assignment,
                    "fileLogger logEvery must be a constant integer.",
                    "Use a literal value, for example logEvery=1000."
            );
        }

        double value =
                number.doubleValue();

        if (value != Math.rint(value)) {
            throw new TileApplicationError(
                    assignment,
                    "fileLogger logEvery must be an integer.",
                    "Use a whole number, for example logEvery=1000."
            );
        }

        return (int) value;
    }

    private static String getStringLiteral(Expr expression, Stmt.Assignment assignment) {
        if (!(expression instanceof Expr.Literal literal)) {
            throw new TileApplicationError(
                    assignment,
                    "fileLogger file must be a constant string.",
                    "Use file=\"output.log\"."
            );
        }

        if (!(literal.value instanceof String fileName)) {
            throw new TileApplicationError(
                    assignment,
                    "fileLogger file must be a constant string.",
                    "Use file=\"output.log\"."
            );
        }

        return fileName;
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.CUSTOM;
    }
}