package tiles.trees;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.speciation.YuleModel;
import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
import beast.base.spec.evolution.tree.coalescent.RandomTree;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.BoundDistribution;
import tiling.Tile;

import java.util.Set;

public class YuleTile extends GeneratorTile<BoundDistribution<Tree, YuleModel>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Yule";
    }

    TileInput<RealScalar<? extends PositiveReal>> birthRateInput = new TileInput<>("birthRate");
    TileInput<Alignment> taxaInput = new TileInput<>("taxa", true);
    TileInput<RealScalar<? extends PositiveReal>> rootAgeInput = new TileInput<>("rootAge", false);

    @Override
    public BoundDistribution<Tree, YuleModel> applyTile(BEASTState beastState) {
        RealScalar<? extends PositiveReal> birthRate = this.birthRateInput.apply(beastState);
        RealScalar<? extends PositiveReal> rootAge = this.rootAgeInput.apply(beastState);
        Alignment taxaAlignment = this.taxaInput.apply(beastState);

        // initialize initial state

        ConstantPopulation populationFunction = new ConstantPopulation();
        beastState.setInput(populationFunction, populationFunction.popSizeParameter, new RealScalarParam<>(1.0, PositiveReal.INSTANCE));

        RandomTree defaultState = new RandomTree();
        beastState.setInput(defaultState, defaultState.taxaInput, taxaAlignment);
        beastState.setInput(defaultState, defaultState.populationFunctionInput, populationFunction);

        // initialize Yule

        YuleModel yuleModel = new YuleModel();
        beastState.setInput(yuleModel, yuleModel.birthDiffRateParameterInput, birthRate);
        if (rootAge != null) beastState.setInput(yuleModel, yuleModel.originHeightParameterInput, rootAge);

        return new BoundDistribution<>(
                yuleModel,
                defaultState,
                tree -> beastState.setInput(yuleModel, yuleModel.treeInput, tree)
        );
    }

    @Override
    protected Tile<?> createInstance() {
        return new YuleTile();
    }

}
