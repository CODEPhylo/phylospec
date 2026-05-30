package tiles.trees;

import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealVector;
import tiling.BeastXCompoundPopulationModel;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;

public class CompoundPopulationFunctionTile extends GeneratorTile<DemographicModel, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "compoundPopulationFunction";
    }

    GeneratorTileInput<List<DemographicModel>, BeastXState> functionsInput =
            new GeneratorTileInput<>("functions");

    GeneratorTileInput<RealVector<? extends PositiveReal>, BeastXState> changeTimesInput =
            new GeneratorTileInput<>("changeTimes");

    @Override
    public DemographicModel applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        List<DemographicModel> functions =
                this.functionsInput.apply(beastState, indexVariables);

        RealVector<? extends PositiveReal> changeTimes =
                this.changeTimesInput.apply(beastState, indexVariables);

        double[] values =
                new double[(int) changeTimes.size()];

        for (int i = 0; i < values.length; i++) {
            values[i] = changeTimes.get(i);
        }

        if (functions.size() < 2) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "compoundPopulationFunction requires at least two population functions.",
                    "Provide two or more population functions.",
                    List.of("compoundPopulationFunction(functions=[f1, f2], changeTimes=[1.0])")
            );
        }

        if (values.length != functions.size() - 1) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "compoundPopulationFunction requires one fewer changeTimes value than functions.",
                    "For n functions, provide n - 1 change times.",
                    List.of("compoundPopulationFunction(functions=[f1, f2, f3], changeTimes=[1.0, 3.0])")
            );
        }

        return new BeastXCompoundPopulationModel(
                "compoundPopulation",
                functions,
                values
        );
    }
}