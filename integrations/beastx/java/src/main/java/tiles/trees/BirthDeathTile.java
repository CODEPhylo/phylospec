package tiles.trees;

import dr.evolution.util.Taxa;
import dr.evolution.util.Units;
import dr.evomodel.speciation.BirthDeathGernhard08Model;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.evomodel.tree.DefaultTreeModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;
import tiling.model.BoundDistribution;
import tiling.params.BeastXParameters;

import java.util.IdentityHashMap;
import java.util.Set;

public class BirthDeathTile extends GeneratorTile<
        BoundDistribution<TreeModel, SpeciationLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "BirthDeath";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> diversificationRateInput =
            new GeneratorTileInput<>("diversificationRate");

    GeneratorTileInput<RealScalar<? extends NonNegativeReal>, BeastXState> turnoverInput =
            new GeneratorTileInput<>("turnover");

    GeneratorTileInput<RealScalar<UnitInterval>, BeastXState> samplingProbabilityInput =
            new GeneratorTileInput<>("samplingProbability", false);

    // The root is fixed at rootAge during MCMC, so a sampled rootAge is not supported.
    GeneratorTileInput<RealScalar<? extends NonNegativeReal>, BeastXState> rootAgeInput =
            new GeneratorTileInput<>("rootAge", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    GeneratorTileInput<Taxa, BeastXState> taxaInput =
            new GeneratorTileInput<>("taxa");

    @Override
    public BoundDistribution<TreeModel, SpeciationLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends PositiveReal> diversificationRate =
                this.diversificationRateInput.apply(beastState, indexVariables);

        RealScalar<? extends NonNegativeReal> turnover =
                this.turnoverInput.apply(beastState, indexVariables);

        RealScalar<UnitInterval> samplingProbability =
                this.samplingProbabilityInput.apply(beastState, indexVariables);

        RealScalar<? extends NonNegativeReal> rootAge =
                this.rootAgeInput.apply(beastState, indexVariables);

        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        Parameter samplingProbabilityParameter =
                samplingProbability == null
                        ? null
                        : BeastXParameters.toParameter(samplingProbability);

        Double conditioningAge =
                RootAge.conditioningAge(
                        this.getRootNode(), beastState, rootAge, taxa, samplingProbabilityParameter, "BirthDeath");

        DefaultTreeModel defaultTreeModel =
                new DefaultTreeModel(
                        "tree",
                        InitialTreeBuilder.balancedTree(taxa, "BirthDeath", rootAge)
                );

        BirthDeathGernhard08Model birthDeathModel =
                conditioningAge == null
                        ? new BirthDeathGernhard08Model(
                                BeastXParameters.toParameter(diversificationRate),
                                BeastXParameters.toParameter(turnover),
                                samplingProbabilityParameter == null
                                        ? new Parameter.Default(1.0)
                                        : samplingProbabilityParameter,
                                BirthDeathGernhard08Model.TreeType.LABELED,
                                Units.Type.YEARS
                        )
                        : RootAge.conditionedModel(
                                "birthDeath",
                                BeastXParameters.toParameter(diversificationRate),
                                BeastXParameters.toParameter(turnover),
                                BirthDeathGernhard08Model.TreeType.LABELED
                        );

        if (conditioningAge != null) {
            RootAge.fixRoot(beastState, defaultTreeModel);
        }

        return new BoundDistribution<>(
                defaultTreeModel,
                treeModel -> {
                    return new SpeciationLikelihood(
                            treeModel,
                            birthDeathModel,
                            "birthDeathPrior"
                    );
                }
        );
    }
}
