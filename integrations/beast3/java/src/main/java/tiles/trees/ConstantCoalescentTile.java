package tiles.trees;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
import beast.base.spec.evolution.tree.coalescent.RandomTree;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import tiles.input.DecoratedAlignment;
import tiling.BEASTState;
import tiling.BoundDistribution;

public class ConstantCoalescentTile extends GeneratorTile<BoundDistribution<Tree, Coalescent>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Coalescent";
    }

    TileInput<RealScalar<? extends PositiveReal>> populationSizeInput = new TileInput<>("populationSize");
    TileInput<DecoratedAlignment> taxaInput = new TileInput<>("taxa", true);

    @Override
    public BoundDistribution<Tree, Coalescent> applyTile(BEASTState beastState) {
        RealScalar<? extends PositiveReal> populationSize = this.populationSizeInput.apply(beastState);
        DecoratedAlignment taxaAlignment = this.taxaInput.apply(beastState);

        // initialize initial state

        ConstantPopulation populationFunction = new ConstantPopulation();
        beastState.setInput(populationFunction, populationFunction.popSizeParameter, new RealScalarParam<>(1.0, PositiveReal.INSTANCE));

        RandomTree defaultState = new RandomTree();
        beastState.setInput(defaultState, defaultState.taxaInput, taxaAlignment.alignment());
        beastState.setInput(defaultState, defaultState.populationFunctionInput, populationFunction);

        // set tip dates if provided

        if (taxaAlignment.ages() != null) {
            defaultState.setDateTrait(taxaAlignment.ages());
        }

        // initialize constant Coalescent

        ConstantPopulation constantPopulation = new ConstantPopulation();
        beastState.setInput(constantPopulation, constantPopulation.popSizeParameter, populationSize);

        Coalescent model = new Coalescent();
        beastState.setInput(model, model.popSizeInput, constantPopulation);

        return new BoundDistribution<>(
                model,
                defaultState,
                tree -> beastState.setInput(model, model.treeInput, tree)
        );
    }

}
