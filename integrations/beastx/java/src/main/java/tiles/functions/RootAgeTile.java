package tiles.functions;

import dr.evomodel.tree.TreeHeightStatistic;
import dr.evomodel.tree.TreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXState;
import tiling.params.BeastXStatisticRealScalar;

import java.util.IdentityHashMap;

/**
 * {@code rootAge(tree)}: the current height of the tree's root.
 *
 * <p>Like {@code mrca}, this is a BEAST X statistic that reads the tree each time, so it follows
 * a sampled tree during MCMC.</p>
 */
public class RootAgeTile extends GeneratorTile<RealScalar<NonNegativeReal>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "rootAge";
    }

    GeneratorTileInput<TreeModel, BeastXState> treeInput =
            new GeneratorTileInput<>("tree");

    @Override
    public RealScalar<NonNegativeReal> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        TreeModel tree =
                this.treeInput.apply(beastState, indexVariables);

        String id =
                this.getId("rootAge", indexVariables, "");

        TreeHeightStatistic statistic =
                new TreeHeightStatistic(id, tree);

        if (statistic.getStatisticValue(0) < 0.0) {
            throw new IllegalArgumentException(
                    "A tree root age must be non-negative."
            );
        }

        beastState.addCalculationNode(
                statistic,
                new TypeToken<TreeHeightStatistic>() {},
                id
        );

        return new BeastXStatisticRealScalar<>(
                statistic,
                NonNegativeReal.INSTANCE
        );
    }
}
