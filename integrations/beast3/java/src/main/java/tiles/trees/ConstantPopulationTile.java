package tiles.trees;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.Map;

public class ConstantPopulationTile extends GeneratorTile<PopulationFunction> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "constantPopulationFunction";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>> populationSizeInput = new GeneratorTileInput<>("populationSize");

    @Override
    public PopulationFunction applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
        RealScalar<? extends PositiveReal> populationSize = this.populationSizeInput.apply(beastState, indexVariables);

        ConstantPopulation constantPopulation = new ConstantPopulation();
        beastState.setInput(constantPopulation, constantPopulation.popSizeParameter, populationSize);

        return constantPopulation;
    }

}
