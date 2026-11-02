package tiles.trees;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
import beast.base.spec.evolution.tree.coalescent.RandomTree;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import java.util.IdentityHashMap;
import java.util.List;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiles.input.TaxaData;
import tiles.input.TaxonValue;
import tiling.BoundDistribution;

public class ConstantCoalescentTile extends GeneratorTile<BoundDistribution<Tree, Coalescent>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Coalescent";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> populationSizeInput =
            new GeneratorTileInput<>("populationSize");
    GeneratorTileInput<List<TaxonValue>, BEASTState> taxaInput = new GeneratorTileInput<>("taxa", true);

    @Override
    public BoundDistribution<Tree, Coalescent> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<? extends PositiveReal> populationSize = this.populationSizeInput.apply(beastState, indexVariables);
        TaxaData taxa = TaxaData.fromList(beastState, this.taxaInput.apply(beastState, indexVariables));

        // initialize initial state

        ConstantPopulation populationFunction = new ConstantPopulation();
        beastState.setInput(
                populationFunction,
                populationFunction.popSizeParameter,
                new RealScalarParam<>(1.0, PositiveReal.INSTANCE));

        RandomTree defaultState = new RandomTree();
        beastState.setInput(defaultState, defaultState.m_taxonset, taxa.taxonSet());
        beastState.setInput(defaultState, defaultState.populationFunctionInput, populationFunction);

        // set tip dates if provided

        if (taxa.ages() != null) {
            defaultState.setDateTrait(taxa.ages());
        }

        // initialize constant Coalescent

        ConstantPopulation constantPopulation = new ConstantPopulation();
        beastState.setInput(constantPopulation, constantPopulation.popSizeParameter, populationSize);

        Coalescent model = new Coalescent();
        beastState.setInput(model, model.popSizeInput, constantPopulation);

        return new BoundDistribution<>(
                model,
                defaultState,
                tree -> {
                    // Expose intervals to BEAST's dirty/store/restore calculation graph.
                    TreeIntervals intervals = new TreeIntervals();
                    beastState.setInput(intervals, intervals.treeInput, tree);
                    beastState.setInput(model, model.treeIntervalsInput, intervals);
                },
                OperatorSelector::getDefaultOperators);
    }
}
