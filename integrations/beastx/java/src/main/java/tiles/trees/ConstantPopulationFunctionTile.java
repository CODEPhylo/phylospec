package tiles.trees;

import dr.evolution.util.Units;
import dr.evomodel.coalescent.demographicmodel.ConstantPopulationModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXState;
import tiling.operators.ParameterRole;
import tiling.params.BeastXParameters;

import java.util.IdentityHashMap;

public class ConstantPopulationFunctionTile extends GeneratorTile<ConstantPopulationModel, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "constantPopulationFunction";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> populationSizeInput =
            new GeneratorTileInput<>("populationSize");

    @Override
    public ConstantPopulationModel applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends PositiveReal> populationSize =
                this.populationSizeInput.apply(beastState, indexVariables);

        Parameter populationSizeParameter =
                BeastXParameters.toParameter(populationSize);

        beastState.addParameterRole(
                populationSizeParameter,
                ParameterRole.DEMOGRAPHIC_SCALE);

        return new ConstantPopulationModel(
                "constantPopulation",
                populationSizeParameter,
                Units.Type.YEARS
        );
    }
}
