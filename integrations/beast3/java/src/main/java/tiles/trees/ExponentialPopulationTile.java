package tiles.trees;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.tree.coalescent.ExponentialGrowth;
import beast.base.spec.type.RealScalar;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;

public class ExponentialPopulationTile extends GeneratorTile<PopulationFunction> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "exponentialPopulationFunction";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>> populationSizeInput = new GeneratorTileInput<>("populationSize");
    GeneratorTileInput<RealScalar<? extends PositiveReal>> growthRateInput = new GeneratorTileInput<>("growthRate");

    @Override
    public PopulationFunction applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<? extends PositiveReal> populationSize = this.populationSizeInput.apply(beastState, indexVariables);
        RealScalar<? extends PositiveReal> growthRate = this.growthRateInput.apply(beastState, indexVariables);

        ExponentialGrowth population = new ExponentialGrowth();
        beastState.setInput(population, population.popSizeParameterInput, populationSize);
        beastState.setInput(population, population.growthRateParameterInput, growthRate);

        return population;
    }

}
