package tiles.misc;

import beastconfig.BEASTState;
import java.util.*;
import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

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
    public Set<Tile<?, BEASTState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BEASTState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt {
        if (!(node instanceof Expr.Array array)) throw new FailedTilingAttempt.Irrelevant();

        // we gather at all possible input tiles for each element

        List<Set<Tile<?, BEASTState>>> allPossibleInputTiles = new ArrayList<>();
        for (Expr element : array.elements) {
            Set<Tile<?, BEASTState>> elementTiles = allInputTiles.get(element);
            allPossibleInputTiles.add(elementTiles);
        }

        // Only tiles with the same element type can form a vector. Group by TypeToken before
        // visiting combinations so that incompatible combinations are never constructed. This is
        // especially important for long numeric vectors, where each literal has several possible
        // Java representations and the unfiltered Cartesian product grows exponentially.

        Map<TypeToken<?>, List<Set<Tile<?, BEASTState>>>> compatibleTilesByType =
                commonElementTypes(allPossibleInputTiles);

        Set<Tile<?, BEASTState>> wiredUpTiles = new HashSet<>();
        for (List<Set<Tile<?, BEASTState>>> compatibleInputTiles : compatibleTilesByType.values()) {
            Utils.visitCombinations(compatibleInputTiles, inputTiles -> {
                Tile<?, BEASTState> tile = new ListVectorTile(inputTiles);
                tile.setRootNode(node);

                int totalWeight = inputTiles.stream().mapToInt(Tile::getWeight).sum();
                tile.setWeight(totalWeight + this.getPriority().getWeight());

                wiredUpTiles.add(tile);
            });
        }

        return wiredUpTiles;
    }

    private static Map<TypeToken<?>, List<Set<Tile<?, BEASTState>>>> commonElementTypes(
            List<Set<Tile<?, BEASTState>>> allPossibleInputTiles) {
        Map<TypeToken<?>, List<Set<Tile<?, BEASTState>>>> compatibleTilesByType = new HashMap<>();

        for (int index = 0; index < allPossibleInputTiles.size(); index++) {
            Map<TypeToken<?>, Set<Tile<?, BEASTState>>> tilesByType = new HashMap<>();
            for (Tile<?, BEASTState> tile : allPossibleInputTiles.get(index)) {
                tilesByType
                        .computeIfAbsent(tile.getTypeToken(), ignored -> new HashSet<>())
                        .add(tile);
            }

            if (index == 0) {
                for (Map.Entry<TypeToken<?>, Set<Tile<?, BEASTState>>> entry : tilesByType.entrySet()) {
                    compatibleTilesByType.put(entry.getKey(), new ArrayList<>(List.of(entry.getValue())));
                }
                continue;
            }

            compatibleTilesByType.keySet().retainAll(tilesByType.keySet());
            for (Map.Entry<TypeToken<?>, List<Set<Tile<?, BEASTState>>>> entry : compatibleTilesByType.entrySet()) {
                entry.getValue().add(tilesByType.get(entry.getKey()));
            }
        }

        return compatibleTilesByType;
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
