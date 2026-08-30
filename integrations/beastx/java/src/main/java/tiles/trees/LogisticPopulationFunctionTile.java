package tiles.trees;

import dr.evolution.util.Units;
import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import dr.evomodel.coalescent.demographicmodel.LogisticGrowthModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.Real;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.operators.ParameterRole;

import java.util.IdentityHashMap;

public class LogisticPopulationFunctionTile extends GeneratorTile<DemographicModel, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "logisticPopulationFunction";
    }

    GeneratorTileInput<RealScalar<? extends NonNegativeReal>, BeastXState> inflectionAgeInput =
            new GeneratorTileInput<>("inflectionAge");

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> carryingCapacityInput =
            new GeneratorTileInput<>("carryingCapacity");

    GeneratorTileInput<RealScalar<? extends Real>, BeastXState> growthRateInput =
            new GeneratorTileInput<>("growthRate");

    @Override
    public DemographicModel applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends NonNegativeReal> inflectionAge =
                this.inflectionAgeInput.apply(beastState, indexVariables);

        RealScalar<? extends PositiveReal> carryingCapacity =
                this.carryingCapacityInput.apply(beastState, indexVariables);

        RealScalar<? extends Real> growthRate =
                this.growthRateInput.apply(beastState, indexVariables);

        Parameter carryingCapacityParameter = toParameter(carryingCapacity);
        Parameter growthRateParameter = toParameter(growthRate);
        Parameter inflectionAgeParameter = toParameter(inflectionAge);

        beastState.addParameterRole(
                carryingCapacityParameter,
                ParameterRole.DEMOGRAPHIC_SCALE);
        beastState.addParameterRole(
                growthRateParameter,
                ParameterRole.DEMOGRAPHIC_GROWTH_RATE);
        beastState.addParameterRole(
                inflectionAgeParameter,
                ParameterRole.DEMOGRAPHIC_SCALE);

        return new LogisticGrowthModel(
                "logisticPopulation",
                carryingCapacityParameter,
                growthRateParameter,
                inflectionAgeParameter,
                0.5,
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
