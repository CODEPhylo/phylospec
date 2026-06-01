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

    private final List<String> loggableNames;
    private final List<Tile<?, BeastXState>> loggableInputTiles;

    public FileLoggerTile() {
        this.loggableNames = null;
        this.loggableInputTiles = List.of();
    }

    private FileLoggerTile(
            List<String> loggableNames,
            List<Tile<?, BeastXState>> loggableInputTiles
    ) {
        this.loggableNames = loggableNames;
        this.loggableInputTiles = loggableInputTiles;
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

        LoggableInputs loggableInputs =
                getLoggableInputs(assignment, allInputTiles);

        if (loggableInputs == null) {
            FileLoggerTile tile =
                    new FileLoggerTile(null, List.of());

            tile.setRootNode(node);
            tile.setWeight(getPriority().getWeight());

            return Set.of(tile);
        }

        if (loggableInputs.possibleInputTiles.isEmpty()) {
            FileLoggerTile tile =
                    new FileLoggerTile(loggableInputs.names, List.of());

            tile.setRootNode(node);
            tile.setWeight(getPriority().getWeight());

            return Set.of(tile);
        }

        Set<Tile<?, BeastXState>> fileLoggerTiles =
                new HashSet<>();

        Utils.visitCombinations(
                loggableInputs.possibleInputTiles,
                selectedInputTiles -> {
                    FileLoggerTile tile =
                            new FileLoggerTile(
                                    loggableInputs.names,
                                    new ArrayList<>(selectedInputTiles)
                            );

                    tile.setRootNode(node);

                    int inputWeight =
                            selectedInputTiles.stream()
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

        for (Tile<?, BeastXState> inputTile : this.loggableInputTiles) {
            inputTile.apply(beastState, indexVariables);
        }

        beastState.addFileLoggerSpec(logEvery, fileName, this.loggableNames);
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

    private static LoggableInputs getLoggableInputs(
            Stmt.Assignment assignment,
            Map<AstNode, Set<Tile<?, BeastXState>>> allInputTiles
    ) throws FailedTilingAttempt {
        Expr.Call call =
                (Expr.Call) assignment.expression;

        for (Expr.Argument argument : call.arguments) {
            if (argument.name != null && argument.name.equals("parameters")) {
                return getNameInputs(argument.expression, assignment, allInputTiles);
            }
        }

        return null;
    }

    private static LoggableInputs getNameInputs(
            Expr expression,
            Stmt.Assignment assignment,
            Map<AstNode, Set<Tile<?, BeastXState>>> allInputTiles
    ) throws FailedTilingAttempt {
        if (!(expression instanceof Expr.Array array)) {
            throw new TileApplicationError(
                    assignment,
                    "fileLogger parameters must be a list of loggable names.",
                    "Use parameters=[clockRate] or parameters=[\"posterior\", \"prior\", \"likelihood\", \"clockRate\"]."
            );
        }

        List<String> names =
                new ArrayList<>();

        List<Set<Tile<?, BeastXState>>> possibleInputTiles =
                new ArrayList<>();

        for (Expr element : array.elements) {
            if (element instanceof Expr.Variable variable) {
                names.add(variable.variableName);

                if (!isModelLevelLoggable(variable.variableName)) {
                    Set<Tile<?, BeastXState>> tiles =
                            allInputTiles.get(variable);

                    if (tiles == null || tiles.isEmpty()) {
                        throw new FailedTilingAttempt.RejectedCascade(variable);
                    }

                    possibleInputTiles.add(tiles);
                }

                continue;
            }

            if (element instanceof Expr.Literal literal && literal.value instanceof String string) {
                names.add(string);
                continue;
            }

            throw new TileApplicationError(
                    assignment,
                    "fileLogger parameters must contain names.",
                    "Use bare parameter names such as clockRate, or strings such as \"posterior\"."
            );
        }

        return new LoggableInputs(names, possibleInputTiles);
    }

    private static boolean isModelLevelLoggable(String name) {
        return name.equals("posterior")
                || name.equals("prior")
                || name.equals("likelihood");
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

    private record LoggableInputs(
            List<String> names,
            List<Set<Tile<?, BeastXState>>> possibleInputTiles
    ) {
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.CUSTOM;
    }
}