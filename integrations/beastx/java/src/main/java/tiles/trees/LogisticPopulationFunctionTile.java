package tiles.trees;

import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.Real;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXParameters;
import tiling.BeastXState;
import tiling.population.BeastXLogisticPopulationModel;

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

        return new BeastXLogisticPopulationModel(
                "logisticPopulation",
                BeastXParameters.toParameter(carryingCapacity),
                BeastXParameters.toParameter(growthRate),
                BeastXParameters.toParameter(inflectionAge)
        );
    }
}