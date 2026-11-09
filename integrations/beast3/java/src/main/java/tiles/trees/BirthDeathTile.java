package tiles.trees;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.evolution.speciation.BirthDeathGernhard08Model;
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

public class BirthDeathTile extends GeneratorTile<BoundDistribution<Tree, BirthDeathGernhard08Model>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "BirthDeath";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> diversificationRateInput =
            new GeneratorTileInput<>("diversificationRate");
    GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> turnoverInput =
            new GeneratorTileInput<>("turnover");
    GeneratorTileInput<RealScalar<UnitInterval>, BEASTState> samplingProbabilityInput =
            new GeneratorTileInput<>("samplingProbability", false);
    GeneratorTileInput<List<TaxonValue>, BEASTState> taxaInput = new GeneratorTileInput<>("taxa", true);
    GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> rootAgeInput =
            new GeneratorTileInput<>("rootAge", false);

    @Override
    public BoundDistribution<Tree, BirthDeathGernhard08Model> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<? extends PositiveReal> diversificationRate =
                this.diversificationRateInput.apply(beastState, indexVariables);
        RealScalar<? extends PositiveReal> turnover = this.turnoverInput.apply(beastState, indexVariables);
        RealScalar<UnitInterval> samplingProbability = this.samplingProbabilityInput.apply(beastState, indexVariables);
        RealScalar<? extends PositiveReal> rootAge = this.rootAgeInput.apply(beastState, indexVariables);
        TaxaData taxa = TaxaData.fromList(beastState, this.taxaInput.apply(beastState, indexVariables));

        RandomTree defaultState = createInitialTree(beastState, taxa);

        // initialize BD

        BirthDeathGernhard08Model birthDeathModel = new BirthDeathGernhard08Model();
        beastState.setInput(birthDeathModel, birthDeathModel.birthDiffRateParameterInput, diversificationRate);
        beastState.setInput(birthDeathModel, birthDeathModel.relativeDeathRateParameterInput, turnover);
        if (samplingProbability != null)
            beastState.setInput(birthDeathModel, birthDeathModel.sampleProbabilityInput, samplingProbability);
        if (rootAge != null) beastState.setInput(birthDeathModel, birthDeathModel.originHeightParameterInput, rootAge);

        return new BoundDistribution<>(
                birthDeathModel,
                defaultState,
                tree -> beastState.setInput(birthDeathModel, birthDeathModel.treeInput, tree),
                OperatorSelector::getDefaultOperators);
    }

    static RandomTree createInitialTree(BEASTState beastState, TaxaData taxa) {
        ConstantPopulation populationFunction = new ConstantPopulation();
        beastState.setInput(
                populationFunction,
                populationFunction.popSizeParameter,
                new RealScalarParam<>(1.0, PositiveReal.INSTANCE));

        RandomTree defaultState = new RandomTree();
        beastState.setInput(defaultState, defaultState.m_taxonset, taxa.taxonSet());
        beastState.setInput(defaultState, defaultState.populationFunctionInput, populationFunction);
        if (taxa.ages() != null) {
            defaultState.setDateTrait(taxa.ages());
        }
        return defaultState;
    }
}
