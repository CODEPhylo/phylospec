package tiles.misc;

import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.tiling.tiles.AstNodeTile;
import beastconfig.BEASTState;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.tiling.TypeToken;

import java.util.*;

/**
 * This tile matches an array and simply creates a Java List with all objects produced by the elements. A vector tile
 * is created for every combination of element tiles which have the same TypeToken.
 */
public class ListVectorTile extends AstNodeTile<List<Object>, Expr.Array, BEASTState> {

    private final List<Tile<?, BEASTState>> inputTiles;

    public ListVectorTile() {
        this.inputTiles = new ArrayList<>();
    }

    public ListVectorTile(List<Tile<?, BEASTState>> inputTiles) {
        this.inputTiles = inputTiles;
    }

    @Override
    public Set<Tile<?, BEASTState>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?, BEASTState>>> allInputTiles, VariableResolver variableResolver, StochasticityResolver stochasticityResolver) throws FailedTilingAttempt {
        if (!(node instanceof Expr.Array array)) throw new FailedTilingAttempt.Irrelevant();

        // we gather at all possible input tiles for each element

        List<Set<Tile<?, BEASTState>>> allPossibleInputTiles = new ArrayList<>();
        for (Expr element : array.elements) {
            Set<Tile<?, BEASTState>> elementTiles = allInputTiles.get(element);
            allPossibleInputTiles.add(elementTiles);
        }

        // we now look at every combination of element tiles. we create a tile for every combination where
        // everything has the same type

        Set<Tile<?, BEASTState>> wiredUpTiles = new HashSet<>();
        Utils.visitCombinations(
                allPossibleInputTiles, inputTiles -> {
                    // make sure that all input tiles have the same type token
                    TypeToken<?> firstToken = inputTiles.getFirst().getTypeToken();
                    if (inputTiles.stream().anyMatch(t -> !Objects.equals(t.getTypeToken(), firstToken))) return;

                    Tile<?, BEASTState> tile = new ListVectorTile(inputTiles);
                    tile.setRootNode(node);

                    int totalWeight = inputTiles.stream().mapToInt(Tile::getWeight).sum();
                    tile.setWeight(totalWeight + this.getPriority().getWeight());

                    wiredUpTiles.add(tile);
                }
        );

        return wiredUpTiles;
    }

    @Override
    public List<Object> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        List<Object> list = new ArrayList<>();

        for (Tile<?, BEASTState> tile : inputTiles) {
            list.add(tile.apply(beastState, indexVariables));
        }

        return list;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        TypeToken<?> valueType = this.inputTiles.getFirst().getTypeToken();
        if (valueType != null) return TypeToken.listOf(valueType);

        // we return the basic vector type
        return super.getTypeToken();
    }

}
