package tiles.branchmodels;

import dr.evomodel.branchratemodel.StrictClockBranchRates;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXState;
import tiling.params.BeastXParameters;

import java.util.IdentityHashMap;

public class StrictClockTile extends GeneratorTile<StrictClockBranchRates, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "StrictClock";
    }

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> clockRateInput =
            new GeneratorTileInput<>("clockRate", false);

    GeneratorTileInput<TreeModel, BeastXState> treeInput =
            new GeneratorTileInput<>("tree");

    @Override
    public StrictClockBranchRates applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<PositiveReal> clockRate =
                this.clockRateInput.apply(beastState, indexVariables);

        TreeModel tree =
                this.treeInput.apply(beastState, indexVariables);

        // clockRate is optional and defaults to 1, as in the PhyloSpec reference.
        Parameter clockRateParameter =
                clockRate == null ? new Parameter.Default(1.0) : BeastXParameters.toParameter(clockRate);

        beastState.addTreeStrictClockRateParameter(
                tree,
                clockRateParameter
        );

        return new StrictClockBranchRates(clockRateParameter);
    }
}
