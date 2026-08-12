package tiles.packages.sampledancestors;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
import beast.base.spec.evolution.tree.coalescent.RandomTree;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import sa.evolution.speciation.SABirthDeathModel;
import tiles.input.DecoratedAlignment;
import tiling.BoundDistribution;

/** Implements the direct-rate FossilizedBirthDeath parameterization. */
public class FossilizedBirthDeathTile
        extends GeneratorTile<BoundDistribution<Tree, SABirthDeathModel>, BEASTState> {

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> speciationRateInput =
            new GeneratorTileInput<>("speciationRate", false);
    GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> extinctionRateInput =
            new GeneratorTileInput<>("extinctionRate", false);
    GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> diversificationRateInput =
            new GeneratorTileInput<>("diversificationRate", false);
    GeneratorTileInput<RealScalar<UnitInterval>, BEASTState> turnoverInput =
            new GeneratorTileInput<>("turnover", false);
    GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> serialSamplingRateInput =
            new GeneratorTileInput<>("serialSamplingRate");
    GeneratorTileInput<RealScalar<UnitInterval>, BEASTState> samplingProbabilityInput =
            new GeneratorTileInput<>("samplingProbability", false);
    GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> rootAgeInput =
            new GeneratorTileInput<>("rootAge", false);
    GeneratorTileInput<DecoratedAlignment, BEASTState> taxaInput =
            new GeneratorTileInput<>("taxa");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "FossilizedBirthDeath";
    }

    @Override
    public BoundDistribution<Tree, SABirthDeathModel> applyTile(
            BEASTState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<? extends PositiveReal> speciationRate =
                this.speciationRateInput.apply(beastState, indexVariables);
        RealScalar<? extends PositiveReal> extinctionRate =
                this.extinctionRateInput.apply(beastState, indexVariables);
        RealScalar<? extends PositiveReal> diversificationRate =
                this.diversificationRateInput.apply(beastState, indexVariables);
        RealScalar<UnitInterval> turnover =
                this.turnoverInput.apply(beastState, indexVariables);
        RealScalar<? extends PositiveReal> serialSamplingRate =
                this.serialSamplingRateInput.apply(beastState, indexVariables);
        RealScalar<UnitInterval> samplingProbability =
                this.samplingProbabilityInput.apply(beastState, indexVariables);
        RealScalar<? extends PositiveReal> rootAge =
                this.rootAgeInput.apply(beastState, indexVariables);
        DecoratedAlignment taxaAlignment = this.taxaInput.apply(beastState, indexVariables);

        ConstantPopulation populationFunction = new ConstantPopulation();
        beastState.setInput(
                populationFunction,
                populationFunction.popSizeParameter,
                new RealScalarParam<>(1.0, PositiveReal.INSTANCE));

        RandomTree defaultState = new RandomTree();
        beastState.setInput(defaultState, defaultState.taxaInput, taxaAlignment.alignment());
        beastState.setInput(defaultState, defaultState.m_taxonset, taxaAlignment.taxonSet());
        beastState.setInput(defaultState, defaultState.populationFunctionInput, populationFunction);
        if (rootAge != null) {
            beastState.setInput(defaultState, defaultState.rootHeightInput, rootAge);
        }
        if (taxaAlignment.ages() != null) {
            defaultState.setDateTrait(taxaAlignment.ages());
        }

        RealScalarParam<UnitInterval> removalProbability =
                new RealScalarParam<>(0.0, UnitInterval.INSTANCE);
        if (samplingProbability == null) {
            samplingProbability = new RealScalarParam<>(1.0, UnitInterval.INSTANCE);
        }

        SABirthDeathModel model = new SABirthDeathModel();
        if (speciationRate != null && extinctionRate != null) {
            beastState.setInput(model, model.birthRateInput, speciationRate);
            beastState.setInput(model, model.deathRateInput, extinctionRate);
        } else if (diversificationRate != null && turnover != null) {
            DiversificationTurnoverRate<PositiveReal> birthRate =
                    DiversificationTurnoverRate.birthRate();
            beastState.setInput(
                    birthRate, birthRate.diversificationRateInput, diversificationRate);
            beastState.setInput(birthRate, birthRate.turnoverInput, turnover);

            DiversificationTurnoverRate<NonNegativeReal> deathRate =
                    DiversificationTurnoverRate.deathRate();
            beastState.setInput(
                    deathRate, deathRate.diversificationRateInput, diversificationRate);
            beastState.setInput(deathRate, deathRate.turnoverInput, turnover);

            beastState.setInput(model, model.birthRateInput, birthRate);
            beastState.setInput(model, model.deathRateInput, deathRate);
        } else {
            throw new IllegalArgumentException(
                    "FossilizedBirthDeath requires either speciation/extinction rates "
                            + "or diversification/turnover.");
        }
        beastState.setInput(model, model.samplingRateInput, serialSamplingRate);
        beastState.setInput(model, model.rhoProbability, samplingProbability);
        beastState.setInput(model, model.removalProbability, removalProbability);
        beastState.setInput(model, model.conditionOnRootInput, true);
        beastState.setInput(model, model.conditionOnRhoSamplingInput, true);

        beastState.setPackageOperatorSelector(
                defaultState,
                state ->
                        SampledAncestorOperatorSelector.addDefaultOperators(
                                defaultState, removalProbability, state));

        return new BoundDistribution<>(
                model,
                defaultState,
                tree -> beastState.setInput(model, model.treeInput, tree));
    }
}
