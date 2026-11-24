package tiles.functions;

import dr.evomodel.tree.TMRCAStatistic;
import dr.evomodel.tree.TreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.RealScalar;
import tiling.BeastXState;
import tiling.params.BeastXStatisticRealScalar;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class AgeNodeTreeTile extends GeneratorTile<RealScalar<NonNegativeReal>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "age";
    }

    GeneratorTileInput<String, BeastXState> nodeInput =
            new GeneratorTileInput<>(
                    "node",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    GeneratorTileInput<TreeModel, BeastXState> treeInput =
            new GeneratorTileInput<>("tree");

    @Override
    public RealScalar<NonNegativeReal> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        String nodeName =
                this.nodeInput.apply(beastState, indexVariables);

        TreeModel tree =
                this.treeInput.apply(beastState, indexVariables);

        // The most recent common ancestor of a single tip is the tip itself (as in BEAST 3).
        // A TMRCAStatistic stays live during MCMC and has a BEAST X XML element.
        TMRCAStatistic statistic =
                CladeTaxa.mrcaStatistic(
                        this.getRootNode(),
                        tree,
                        List.of(nodeName),
                        "Age a = age(node=\"taxon1\", tree=tree)"
                );

        statistic.setId(
                beastState.getAvailableID(
                        this.getId("nodeAge", indexVariables, "")
                )
        );

        if (statistic.getStatisticValue(0) < 0.0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Node age must be non-negative.",
                    "Use a tree whose node heights are non-negative.",
                    List.of("Age a = age(node=\"taxon1\", tree=tree)")
            );
        }

        beastState.addCalculationNode(
                statistic,
                new TypeToken<TMRCAStatistic>() {},
                statistic.getId()
        );

        return new BeastXStatisticRealScalar<>(
                statistic,
                NonNegativeReal.INSTANCE
        );
    }
}
