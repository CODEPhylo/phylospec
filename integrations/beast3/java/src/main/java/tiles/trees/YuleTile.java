package tiles.trees;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.evolution.speciation.YuleModel;
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
import tiling.LiveYuleModel;
import tiling.RootAgeSupport;

public class YuleTile extends GeneratorTile<BoundDistribution<Tree, YuleModel>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Yule";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> birthRateInput =
            new GeneratorTileInput<>("birthRate");
    GeneratorTileInput<List<TaxonValue>, BEASTState> taxaInput = new GeneratorTileInput<>("taxa", true);
    GeneratorTileInput<RealScalar<? extends Real>, BEASTState> rootAgeInput =
            new GeneratorTileInput<>("rootAge", false);

    @Override
    public BoundDistribution<Tree, YuleModel> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<? extends PositiveReal> birthRate = this.birthRateInput.apply(beastState, indexVariables);
        RealScalar<? extends Real> rootAge = this.rootAgeInput.apply(beastState, indexVariables);
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

        // initialize Yule

        LiveYuleModel yuleModel = new LiveYuleModel();
        beastState.setInput(yuleModel, yuleModel.birthDiffRateParameterInput, birthRate);
        RootAgeSupport.prepare(
                beastState, yuleModel, yuleModel.rootAgeParameterInput, defaultState, rootAge, getRootNode());

        return new BoundDistribution<>(
                yuleModel,
                defaultState,
                tree -> RootAgeSupport.bindTree(beastState, yuleModel, tree, taxa, rootAge),
                OperatorSelector::getDefaultOperators);
    }
}
