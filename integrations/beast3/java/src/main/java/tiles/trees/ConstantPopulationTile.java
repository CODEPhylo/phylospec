package tiles.trees;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import tiling.BEASTState;

public class ConstantPopulationTile extends GeneratorTile<PopulationFunction> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "constantPopulationFunction";
    }

    TileInput<RealScalar<? extends PositiveReal>> populationSizeInput = new TileInput<>("populationSize");

    @Override
    public PopulationFunction applyTile(BEASTState beastState) {
        RealScalar<? extends PositiveReal> populationSize = this.populationSizeInput.apply(beastState);

        ConstantPopulation constantPopulation = new ConstantPopulation();
        beastState.setInput(constantPopulation, constantPopulation.popSizeParameter, populationSize);

        return constantPopulation;
    }

}
