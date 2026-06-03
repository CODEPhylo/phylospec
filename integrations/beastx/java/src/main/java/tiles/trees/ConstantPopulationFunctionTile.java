package tiles.trees;

import dr.evolution.util.Units;
import dr.evomodel.coalescent.demographicmodel.ConstantPopulationModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;

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

        return new ConstantPopulationModel(
                "constantPopulation",
                toParameter(populationSize),
                Units.Type.YEARS
        );
    }

    private static Parameter toParameter(RealScalar<?> scalar) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return beastXScalar.getParameter();
        }

        return new Parameter.Default(scalar.get());
    }
}
