package tiles.trees;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.evolution.speciation.BirthDeathGernhard08Model;
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

public class BirthDeathTile extends GeneratorTile<BoundDistribution<Tree, BirthDeathGernhard08Model>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "BirthDeath";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>> diversificationRateInput = new GeneratorTileInput<>("diversificationRate");
    GeneratorTileInput<RealScalar<? extends PositiveReal>> turnoverInput = new GeneratorTileInput<>("turnover");
    GeneratorTileInput<RealScalar<UnitInterval>> samplingProbabilityInput = new GeneratorTileInput<>("samplingProbability", false);
    GeneratorTileInput<DecoratedAlignment> taxaInput = new GeneratorTileInput<>("taxa", true);
    GeneratorTileInput<RealScalar<? extends PositiveReal>> rootAgeInput = new GeneratorTileInput<>("rootAge", false);

    @Override
    public BoundDistribution<Tree, BirthDeathGernhard08Model> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<? extends PositiveReal> diversificationRate = this.diversificationRateInput.apply(beastState, indexVariables);
        RealScalar<? extends PositiveReal> turnover = this.turnoverInput.apply(beastState, indexVariables);
        RealScalar<UnitInterval> samplingProbability = this.samplingProbabilityInput.apply(beastState, indexVariables);
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

        // initialize BD

        BirthDeathGernhard08Model birthDeathModel = new BirthDeathGernhard08Model();
        beastState.setInput(birthDeathModel, birthDeathModel.birthDiffRateParameterInput, diversificationRate);
        beastState.setInput(birthDeathModel, birthDeathModel.relativeDeathRateParameterInput, turnover);
        if (samplingProbability != null) beastState.setInput(birthDeathModel, birthDeathModel.sampleProbabilityInput, samplingProbability);
        if (rootAge != null) beastState.setInput(birthDeathModel, birthDeathModel.originHeightParameterInput, rootAge);

        return new BoundDistribution<>(
                birthDeathModel,
                defaultState,
                tree -> beastState.setInput(birthDeathModel, birthDeathModel.treeInput, tree)
        );
    }

}
