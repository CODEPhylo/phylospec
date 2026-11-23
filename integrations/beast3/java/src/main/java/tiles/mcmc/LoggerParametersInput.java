package tiles.mcmc;

import beast.base.core.BEASTObject;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.InconsistentTilingException;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.tiling.tiles.TileInput;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;

/// Matches the logged parameters of a logger. The elements can have different types as long as each
/// of them is a BEAST object.
public class LoggerParametersInput extends TileInput<List<BEASTObject>, BEASTState> {

    private static final TypeToken<?> BEAST_OBJECT = TypeToken.of(BEASTObject.class);

    private final String templateVariable;

    public LoggerParametersInput(String templateVariable) {
        super(false, EnumSet.allOf(Stochasticity.class));
        this.templateVariable = templateVariable;
    }

    @Override
    public Set<Tile<?, BEASTState>> getCompatibleInputTiles(
            AstNode inputAstNode,
            Map<AstNode, Set<Tile<?, BEASTState>>> possibleInputTiles,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt.RejectedCascade, FailedTilingAttempt.RejectedBoundary {
        if (!(inputAstNode instanceof Expr.Array array)) {
            throw new FailedTilingAttempt.RejectedBoundary("Logger parameters must be an array.");
        }

        List<Set<Tile<?, BEASTState>>> elementTiles = new ArrayList<>();
        for (Expr element : array.elements) {
            elementTiles.add(this.getLoggableTiles(element, possibleInputTiles));
        }

        if (elementTiles.isEmpty()) {
            return Set.of(this.createParametersTile(inputAstNode, List.of()));
        }

        Set<Tile<?, BEASTState>> tiles = new HashSet<>();
        Utils.visitCombinations(
                elementTiles,
                selectedTiles -> tiles.add(this.createParametersTile(inputAstNode, new ArrayList<>(selectedTiles))));
        return tiles;
    }

    private Set<Tile<?, BEASTState>> getLoggableTiles(
            Expr element, Map<AstNode, Set<Tile<?, BEASTState>>> possibleInputTiles)
            throws FailedTilingAttempt.RejectedCascade, FailedTilingAttempt.RejectedBoundary {
        Set<Tile<?, BEASTState>> tiles = possibleInputTiles.get(element);
        if (tiles == null || tiles.isEmpty()) {
            throw new FailedTilingAttempt.RejectedCascade(element);
        }

        Set<Tile<?, BEASTState>> loggableTiles = new HashSet<>();
        for (Tile<?, BEASTState> tile : tiles) {
            if (BEAST_OBJECT.isAssignableFrom(tile.getTypeToken())) loggableTiles.add(tile);
        }

        if (loggableTiles.isEmpty()) {
            throw new FailedTilingAttempt.RejectedBoundary("BEAST 2.8 cannot log this value.");
        }
        return loggableTiles;
    }

    private Tile<?, BEASTState> createParametersTile(AstNode rootNode, List<Tile<?, BEASTState>> inputTiles) {
        LoggerParametersTile tile = new LoggerParametersTile(inputTiles);
        tile.setRootNode(rootNode);
        tile.setWeight(inputTiles.stream().mapToInt(Tile::getWeight).sum());
        return tile;
    }

    @Override
    public String getKey() {
        return this.templateVariable;
    }

    private static class LoggerParametersTile extends Tile<List<BEASTObject>, BEASTState> {

        private final List<Tile<?, BEASTState>> inputTiles;

        private LoggerParametersTile(List<Tile<?, BEASTState>> inputTiles) {
            this.inputTiles = List.copyOf(inputTiles);
        }

        @Override
        protected List<BEASTObject> applyTile(
                BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            List<BEASTObject> parameters = new ArrayList<>();
            for (Tile<?, BEASTState> inputTile : this.inputTiles) {
                parameters.add((BEASTObject) inputTile.apply(state, indexVariables));
            }
            return parameters;
        }

        /**
         * Exposes the element tiles to the consistency check, so that a logged variable is the same
         * object as the one used by the model.
         */
        @Override
        public Map<AstNode, Tile<?, ?>> getWiredUpInputs() throws InconsistentTilingException {
            Map<AstNode, Tile<?, ?>> inputs = new IdentityHashMap<>();
            for (Tile<?, BEASTState> tile : this.inputTiles) {
                Tile<?, ?> previous = inputs.putIfAbsent(tile.getRootNode(), tile);
                if (previous != null && previous != tile) {
                    throw new InconsistentTilingException(tile.getRootNode(), previous, tile);
                }
            }
            return inputs;
        }
    }
}
