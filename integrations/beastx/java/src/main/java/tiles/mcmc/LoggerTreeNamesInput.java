package tiles.mcmc;

import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.TileInput;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.DimensionResolver;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import tiling.BeastXState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LoggerTreeNamesInput extends TileInput<LoggerTreeNames, BeastXState> {

    private final String templateVariable;

    public LoggerTreeNamesInput(String templateVariable, boolean required) {
        super(required, EnumSet.allOf(Stochasticity.class));

        if (!templateVariable.startsWith("$")) {
            throw new RuntimeException(
                    "Invalid template variable '" + templateVariable + "'."
            );
        }

        if (!required && !templateVariable.startsWith("$$")) {
            throw new RuntimeException(
                    "Invalid optional template variable '" + templateVariable + "'."
            );
        }

        this.templateVariable = templateVariable;
    }

    @Override
    public Set<Tile<?, BeastXState>> getCompatibleInputTiles(
            AstNode inputAstNode,
            Map<AstNode, Set<Tile<?, BeastXState>>> possibleInputTiles,
            StochasticityResolver stochasticityResolver,
            DimensionResolver dimensionResolver
    ) throws FailedTilingAttempt.RejectedCascade, FailedTilingAttempt.RejectedBoundary {
        if (!(inputAstNode instanceof Expr.Array array)) {
            throw new FailedTilingAttempt.RejectedBoundary(
                    "BEAST X tree logger trees must be an array."
            );
        }

        List<String> names = new ArrayList<>();
        List<Set<Tile<?, BeastXState>>> allPossibleInputTiles = new ArrayList<>();

        for (Expr element : array.elements) {
            if (!(element instanceof Expr.Variable variable)) {
                throw new FailedTilingAttempt.RejectedBoundary(
                        "BEAST X tree logger trees must be tree variable names."
                );
            }

            names.add(variable.variableName);

            Set<Tile<?, BeastXState>> tiles = possibleInputTiles.get(variable);

            if (tiles == null || tiles.isEmpty()) {
                throw new FailedTilingAttempt.RejectedCascade(variable);
            }

            allPossibleInputTiles.add(tiles);
        }

        Set<Tile<?, BeastXState>> tiles = new HashSet<>();

        Utils.visitCombinations(
                allPossibleInputTiles,
                selectedInputTiles -> {
                    LoggerTreeNamesTile tile =
                            new LoggerTreeNamesTile(names, new ArrayList<>(selectedInputTiles));

                    tile.setRootNode(inputAstNode);

                    int inputWeight =
                            selectedInputTiles.stream()
                                    .mapToInt(Tile::getWeight)
                                    .sum();

                    tile.setWeight(inputWeight);
                    tiles.add(tile);
                }
        );

        return tiles;
    }

    private static class LoggerTreeNamesTile extends Tile<LoggerTreeNames, BeastXState> {

        private final List<String> names;
        private final List<Tile<?, BeastXState>> inputTiles;

        private LoggerTreeNamesTile(
                List<String> names,
                List<Tile<?, BeastXState>> inputTiles
        ) {
            this.names = List.copyOf(names);
            this.inputTiles = List.copyOf(inputTiles);
        }

        @Override
        protected LoggerTreeNames applyTile(
                BeastXState beastState,
                IdentityHashMap<Expr.Variable, Integer> indexVariables
        ) {
            for (Tile<?, BeastXState> inputTile : this.inputTiles) {
                inputTile.apply(beastState, indexVariables);
            }

            return new LoggerTreeNames(this.names);
        }
    }

    @Override
    public String getKey() {
        return this.templateVariable;
    }
}
