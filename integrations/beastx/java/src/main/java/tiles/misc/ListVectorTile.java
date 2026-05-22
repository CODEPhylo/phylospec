package tiles.misc;

import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.BeastXState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ListVectorTile extends AstNodeTile<List<Object>, Expr.Array, BeastXState> {

    private final List<Tile<?, BeastXState>> inputTiles;

    public ListVectorTile() {
        this.inputTiles = new ArrayList<>();
    }

    public ListVectorTile(List<Tile<?, BeastXState>> inputTiles) {
        this.inputTiles = inputTiles;
    }

    @Override
    public Set<Stochasticity> getCompatibleStochasticities() {
        return Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC);
    }

    @Override
    public Set<Tile<?, BeastXState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BeastXState>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt {
        if (!(node instanceof Expr.Array array)) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        Stochasticity stochasticity =
                stochasticityResolver.getStochasticity(node);

        if (!this.getCompatibleStochasticities().contains(stochasticity)) {
            throw new FailedTilingAttempt.Rejected(
                    Stochasticity.getErrorMessage(
                            "BEAST X",
                            stochasticity,
                            this.getCompatibleStochasticities()
                    )
            );
        }

        if (array.elements.isEmpty()) {
            throw new FailedTilingAttempt.Rejected(
                    "BEAST X cannot handle empty arrays."
            );
        }

        List<Set<Tile<?, BeastXState>>> allPossibleInputTiles =
                new ArrayList<>();

        for (Expr element : array.elements) {
            Set<Tile<?, BeastXState>> elementTiles =
                    allInputTiles.get(element);

            allPossibleInputTiles.add(elementTiles);
        }

        Set<Tile<?, BeastXState>> vectorTiles =
                new HashSet<>();

        Utils.visitCombinations(
                allPossibleInputTiles,
                inputTiles -> {
                    TypeToken<?> firstToken =
                            inputTiles.get(0).getTypeToken();

                    if (inputTiles.stream().anyMatch(
                            tile -> !Objects.equals(tile.getTypeToken(), firstToken)
                    )) {
                        return;
                    }

                    Tile<?, BeastXState> tile =
                            new ListVectorTile(inputTiles);

                    tile.setRootNode(node);

                    int totalWeight =
                            inputTiles.stream()
                                    .mapToInt(Tile::getWeight)
                                    .sum();

                    tile.setWeight(
                            totalWeight + this.getPriority().getWeight()
                    );

                    vectorTiles.add(tile);
                }
        );

        if (vectorTiles.isEmpty()) {
            throw new FailedTilingAttempt.Rejected(
                    "BEAST X cannot build a list vector for this array."
            );
        }

        return vectorTiles;
    }

    @Override
    public List<Object> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        List<Object> list =
                new ArrayList<>();

        for (Tile<?, BeastXState> tile : this.inputTiles) {
            list.add(
                    tile.apply(beastState, indexVariables)
            );
        }

        return list;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        if (this.inputTiles.isEmpty()) {
            return super.getTypeToken();
        }

        TypeToken<?> valueType =
                this.inputTiles.get(0).getTypeToken();

        if (valueType != null) {
            return TypeToken.listOf(valueType);
        }

        return super.getTypeToken();
    }
}
