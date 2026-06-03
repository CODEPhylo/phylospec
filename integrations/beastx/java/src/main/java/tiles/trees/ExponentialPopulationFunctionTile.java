package tiles.trees;

import dr.evolution.util.Units;
import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import dr.evomodel.coalescent.demographicmodel.ExponentialGrowthModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.Real;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class ExponentialPopulationFunctionTile extends GeneratorTile<DemographicModel, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "exponentialPopulationFunction";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> populationSizeInput =
            new GeneratorTileInput<>("populationSize");

    GeneratorTileInput<RealScalar<? extends Real>, BeastXState> growthRateInput =
            new GeneratorTileInput<>("growthRate");

    @Override
    public DemographicModel applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends PositiveReal> populationSize =
                this.populationSizeInput.apply(beastState, indexVariables);

        RealScalar<? extends Real> growthRate =
                this.growthRateInput.apply(beastState, indexVariables);

        return new ExponentialGrowthModel(
                "exponentialPopulation",
                toParameter(populationSize),
                toParameter(growthRate),
                Units.Type.YEARS,
                true
        );
    }

    private static Parameter toParameter(RealScalar<?> scalar) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return beastXScalar.getParameter();
        }

        return new Parameter.Default(scalar.get());
    }
}
