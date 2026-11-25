package tiles.mcmc;

import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.tiling.tiles.TileInput;
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

/// Matches the logged parameters of a logger. BEAST X loggers refer to the logged values by name, so
/// every element has to be a variable of the model.
public class LoggerParameterNamesInput extends TileInput<List<String>, BeastXState> {

    private final String templateVariable;

    public LoggerParameterNamesInput(String templateVariable) {
        super(false, EnumSet.allOf(Stochasticity.class));
        this.templateVariable = templateVariable;
    }

    @Override
    public Set<Tile<?, BeastXState>> getCompatibleInputTiles(
            AstNode inputAstNode,
            Map<AstNode, Set<Tile<?, BeastXState>>> possibleInputTiles,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt.RejectedCascade, FailedTilingAttempt.RejectedBoundary {
        if (!(inputAstNode instanceof Expr.Array array)) {
            throw new FailedTilingAttempt.RejectedBoundary("BEAST X logger parameters must be an array.");
        }

        List<String> names = new ArrayList<>();
        List<Set<Tile<?, BeastXState>>> elementTiles = new ArrayList<>();
        for (Expr element : array.elements) {
            if (!(element instanceof Expr.Variable variable)) {
                throw new FailedTilingAttempt.RejectedBoundary("BEAST X logger parameters must be variables.");
            }

            Set<Tile<?, BeastXState>> tiles = possibleInputTiles.get(variable);
            if (tiles == null || tiles.isEmpty()) {
                throw new FailedTilingAttempt.RejectedCascade(variable);
            }

            names.add(variable.variableName);
            elementTiles.add(tiles);
        }

        Set<Tile<?, BeastXState>> tiles = new HashSet<>();
        Utils.visitCombinations(elementTiles, selectedTiles -> {
            Tile<?, BeastXState> tile = new LoggerParameterNamesTile(names, selectedTiles);
            tile.setRootNode(inputAstNode);
            tile.setWeight(selectedTiles.stream().mapToInt(Tile::getWeight).sum());
            tiles.add(tile);
        });
        return tiles;
    }

    @Override
    public String getKey() {
        return this.templateVariable;
    }

    /** Applies the element tiles, so that the logged values exist, and returns their names. */
    private static class LoggerParameterNamesTile extends Tile<List<String>, BeastXState> {

        private final List<String> names;
        private final List<Tile<?, BeastXState>> inputTiles;

        private LoggerParameterNamesTile(List<String> names, List<Tile<?, BeastXState>> inputTiles) {
            this.names = List.copyOf(names);
            this.inputTiles = List.copyOf(inputTiles);
        }

        @Override
        protected List<String> applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            for (Tile<?, BeastXState> inputTile : this.inputTiles) {
                inputTile.apply(beastState, indexVariables);
            }
            return this.names;
        }
    }
}
