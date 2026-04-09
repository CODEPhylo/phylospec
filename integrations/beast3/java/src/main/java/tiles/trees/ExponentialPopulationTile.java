package tiles.trees;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.tree.coalescent.ExponentialGrowth;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import tiling.BEASTState;

public class ExponentialPopulationTile extends GeneratorTile<PopulationFunction> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "exponentialPopulationFunction";
    }

    TileInput<RealScalar<? extends PositiveReal>> populationSizeInput = new TileInput<>("populationSize");
    TileInput<RealScalar<? extends PositiveReal>> growthRateInput = new TileInput<>("growthRate");

    @Override
    public PopulationFunction applyTile(BEASTState beastState) {
        RealScalar<? extends PositiveReal> populationSize = this.populationSizeInput.apply(beastState);
        RealScalar<? extends PositiveReal> growthRate = this.growthRateInput.apply(beastState);

        ExponentialGrowth population = new ExponentialGrowth();
        beastState.setInput(population, population.popSizeParameterInput, populationSize);
        beastState.setInput(population, population.growthRateParameterInput, populationSize);

        return population;
    }

}
