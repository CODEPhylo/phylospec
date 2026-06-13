package tiles.trees;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
import beast.base.spec.evolution.tree.coalescent.RandomTree;
import beast.base.spec.inference.parameter.RealScalarParam;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import tiles.input.DecoratedAlignment;
import beastconfig.BEASTState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;

public class CoalescentTile extends GeneratorTile<BoundDistribution<Tree, Coalescent>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Coalescent";
    }

    GeneratorTileInput<PopulationFunction> populationSizeInput = new GeneratorTileInput<>("populationSize");
    GeneratorTileInput<DecoratedAlignment> taxaInput = new GeneratorTileInput<>("taxa", true);

    @Override
    public BoundDistribution<Tree, Coalescent> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        PopulationFunction populationSize = this.populationSizeInput.apply(beastState, indexVariables);
        DecoratedAlignment taxaAlignment = this.taxaInput.apply(beastState, indexVariables);

        // initialize initial state

        ConstantPopulation populationFunction = new ConstantPopulation();
        beastState.setInput(populationFunction, populationFunction.popSizeParameter, new RealScalarParam<>(1.0, PositiveReal.INSTANCE));

        RandomTree defaultState = new RandomTree();
        beastState.setInput(defaultState, defaultState.taxaInput, taxaAlignment.alignment());
        beastState.setInput(defaultState, defaultState.m_taxonset, taxaAlignment.taxonSet());
        beastState.setInput(defaultState, defaultState.populationFunctionInput, populationFunction);

        // set tip dates if provided

        if (taxaAlignment.ages() != null) {
            defaultState.setDateTrait(taxaAlignment.ages());
        }

        // initialize Coalescent

        Coalescent model = new Coalescent();
        beastState.setInput(model, model.popSizeInput, populationSize);

        return new BoundDistribution<>(
                model,
                defaultState,
                tree -> beastState.setInput(model, model.treeInput, tree)
        );
    }

}
