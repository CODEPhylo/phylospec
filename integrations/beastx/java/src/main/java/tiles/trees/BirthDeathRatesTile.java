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
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXState;
import tiling.model.BoundDistribution;
import tiling.params.BeastXBirthMinusDeathRate;
import tiling.params.BeastXParameters;
import tiling.params.BeastXRelativeDeathRate;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * BirthDeath(birthRate, deathRate). BEAST X's model takes the diversification rate and the turnover,
 * so they are derived from the two rates and follow them when they are sampled.
 *
 * <p>As in BEAST 3, birthRate must be greater than deathRate: the model is not defined for the other
 * rates. Fixed rates with birthRate &lt;= deathRate fail explicitly, and a sampled rate whose
 * starting value violates it is moved into the defined region; a proposal that reaches it during MCMC
 * makes the density NaN, which BEAST X reports as a numerical error and rejects.</p>
 */
public class BirthDeathRatesTile extends GeneratorTile<
        BoundDistribution<TreeModel, SpeciationLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "BirthDeath";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> birthRateInput =
            new GeneratorTileInput<>("birthRate");

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> deathRateInput =
            new GeneratorTileInput<>("deathRate");

    GeneratorTileInput<RealScalar<UnitInterval>, BeastXState> samplingProbabilityInput =
            new GeneratorTileInput<>("samplingProbability", false);

    // A fixed rootAge keeps the root fixed; a sampled rootAge is the root height (see RootAge).
    GeneratorTileInput<RealScalar<? extends NonNegativeReal>, BeastXState> rootAgeInput =
            new GeneratorTileInput<>("rootAge", false);

    GeneratorTileInput<Taxa, BeastXState> taxaInput =
            new GeneratorTileInput<>("taxa");

    @Override
    public BoundDistribution<TreeModel, SpeciationLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Parameter birthRate =
                BeastXParameters.toParameter(this.birthRateInput.apply(beastState, indexVariables));

        Parameter deathRate =
                BeastXParameters.toParameter(this.deathRateInput.apply(beastState, indexVariables));

        RealScalar<UnitInterval> samplingProbability =
                this.samplingProbabilityInput.apply(beastState, indexVariables);

        RealScalar<? extends NonNegativeReal> rootAge =
                this.rootAgeInput.apply(beastState, indexVariables);

        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        // Sampled parameters all start at the same value, which would put a start at birthRate = deathRate.
        // A starting value is not part of the model, so move a sampled rate into the defined region.
        if (!(birthRate.getParameterValue(0) > deathRate.getParameterValue(0))) {
            if (beastState.stateNodes.containsKey(deathRate)) {
                deathRate.setParameterValue(0, birthRate.getParameterValue(0) / 2.0);
            } else if (beastState.stateNodes.containsKey(birthRate)) {
                birthRate.setParameterValue(0, deathRate.getParameterValue(0) * 2.0);
            }
        }

        if (!(birthRate.getParameterValue(0) > deathRate.getParameterValue(0))) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "BirthDeath(birthRate, deathRate) needs birthRate > deathRate, but starts at birthRate="
                            + birthRate.getParameterValue(0) + " and deathRate=" + deathRate.getParameterValue(0) + ".",
                    "The model is not defined otherwise (as in BEAST 3). Use rates or priors whose starting values have birthRate > deathRate.",
                    List.of("Tree tree ~ BirthDeath(birthRate=2.0, deathRate=1.0, taxa=taxa)")
            );
        }

        Parameter diversificationRate =
                new BeastXBirthMinusDeathRate(birthRate, deathRate);

        Parameter turnover =
                new BeastXRelativeDeathRate(birthRate, deathRate);

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
                                diversificationRate,
                                turnover,
                                samplingProbabilityParameter == null
                                        ? new Parameter.Default(1.0)
                                        : samplingProbabilityParameter,
                                BirthDeathGernhard08Model.TreeType.LABELED,
                                Units.Type.YEARS
                        )
                        : RootAge.conditionedModel(
                                "birthDeath",
                                diversificationRate,
                                turnover,
                                BirthDeathGernhard08Model.TreeType.LABELED
                        );

        if (conditioningAge != null) {
            RootAge.bindRoot(this.getRootNode(), beastState, rootAge, defaultTreeModel, "BirthDeath");
        }

        return new BoundDistribution<>(
                defaultTreeModel,
                treeModel -> new SpeciationLikelihood(
                        treeModel,
                        birthDeathModel,
                        "birthDeathPrior"
                )
        );
    }
}
