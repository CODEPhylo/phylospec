package tiles.misc;

import beast.base.core.BEASTObject;
import beast.base.spec.inference.parameter.*;
import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiles.AstNodeTile;
import tiling.BEASTState;
import tiling.FailedTilingAttempt;
import tiling.Tile;

import java.util.*;

public class ListVectorTile<T> extends AstNodeTile<List<BEASTObject>, Expr.Array> {

    private final List<Tile<?>> inputTiles;

    public ListVectorTile() {
        this.inputTiles = new ArrayList<>();
    }

    public ListVectorTile(List<Tile<?>> inputTiles) {
        this.inputTiles = inputTiles;
    }

    @Override
    public Class<Expr.Array> getTargetNodeType() {
        return Expr.Array.class;
    }

    @Override
    public Set<Tile<?>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> allInputTiles, VariableResolver variableResolver, StochasticityResolver stochasticityResolver) throws FailedTilingAttempt {
        if (!(node instanceof Expr.Array array)) throw new FailedTilingAttempt.Irrelevant();

        List<Set<Tile<?>>> allPossibleInputTiles = new ArrayList<>();
        for (Expr element : array.elements) {
            Set<Tile<?>> elementTiles = allInputTiles.get(element);
            allPossibleInputTiles.add(elementTiles);
        }

        Set<Tile<?>> wiredUpTiles = new HashSet<>();
        Utils.visitCombinations(
                allPossibleInputTiles, inputTiles -> {
                    Tile<?> tile = new ListVectorTile<>(inputTiles);
                    tile.setRootNode(node);

                    int totalWeight = inputTiles.stream().mapToInt(Tile::getWeight).sum();
                    tile.setWeight(totalWeight + this.getPriority().getWeight());

                    wiredUpTiles.add(tile);
                }
        );

        return wiredUpTiles;
    }

    @Override
    public List<BEASTObject> applyTile(BEASTState beastState) {
        List<BEASTObject> list = new ArrayList<>();

        for (Tile<?> tile : inputTiles) {
            list.add(beastState.addBEASTObject(tile.apply(beastState)));
        }

        return list;
    }

}
