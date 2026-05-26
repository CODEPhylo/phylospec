package tiles.branchmodels;

import dr.evomodel.branchratemodel.StrictClockBranchRates;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class StrictClockTile extends GeneratorTile<StrictClockBranchRates, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "StrictClock";
    }

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> clockRateInput =
            new GeneratorTileInput<>("clockRate");

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

        Parameter clockRateParameter =
                toParameter(clockRate);

        beastState.addTreeClockRateParameter(
                tree,
                clockRateParameter
        );

        return new StrictClockBranchRates(clockRateParameter);
    }

    private static Parameter toParameter(RealScalar<?> scalar) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return beastXScalar.getParameter();
        }

        return new Parameter.Default(scalar.get());
    }
}