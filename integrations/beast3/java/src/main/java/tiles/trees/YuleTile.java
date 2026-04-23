package tiles.trees;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.speciation.YuleModel;
import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
import beast.base.spec.evolution.tree.coalescent.RandomTree;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import tiles.input.DecoratedAlignment;
import beastconfig.BEASTState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;

public class YuleTile extends GeneratorTile<BoundDistribution<Tree, YuleModel>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Yule";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>> birthRateInput = new GeneratorTileInput<>("birthRate");
    GeneratorTileInput<DecoratedAlignment> taxaInput = new GeneratorTileInput<>("taxa", true);
    GeneratorTileInput<RealScalar<? extends PositiveReal>> rootAgeInput = new GeneratorTileInput<>("rootAge", false);

    @Override
    public BoundDistribution<Tree, YuleModel> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<? extends PositiveReal> birthRate = this.birthRateInput.apply(beastState, indexVariables);
        RealScalar<? extends PositiveReal> rootAge = this.rootAgeInput.apply(beastState, indexVariables);
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

}
