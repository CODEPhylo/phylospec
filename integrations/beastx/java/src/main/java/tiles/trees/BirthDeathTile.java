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
import tiling.BeastXState;
import tiling.model.BoundDistribution;
import tiling.operators.ParameterRole;
import tiling.params.BeastXParameters;

import java.util.IdentityHashMap;

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

    GeneratorTileInput<RealScalar<? extends NonNegativeReal>, BeastXState> rootAgeInput =
            new GeneratorTileInput<>("rootAge", false);

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

        DefaultTreeModel defaultTreeModel =
                new DefaultTreeModel(
                        "tree",
                        InitialTreeBuilder.balancedTree(taxa, "BirthDeath", rootAge)
                );

        Parameter samplingProbabilityParameter =
                samplingProbability == null
                        ? new Parameter.Default(1.0)
                        : BeastXParameters.toParameter(samplingProbability);

        Parameter diversificationRateParameter =
                BeastXParameters.toParameter(diversificationRate);
        Parameter turnoverParameter =
                BeastXParameters.toParameter(turnover);

        constrainTurnover(turnoverParameter);

        beastState.addParameterRole(
                diversificationRateParameter,
                ParameterRole.TREE_PRIOR_SCALE);
        beastState.addParameterRole(
                turnoverParameter,
                ParameterRole.TREE_PRIOR_PROPORTION);
        if (samplingProbability != null) {
            beastState.addParameterRole(
                    samplingProbabilityParameter,
                    ParameterRole.TREE_PRIOR_SCALE);
        }

        BirthDeathGernhard08Model birthDeathModel =
                new BirthDeathGernhard08Model(
                        diversificationRateParameter,
                        turnoverParameter,
                        samplingProbabilityParameter,
                        BirthDeathGernhard08Model.TreeType.LABELED,
                        Units.Type.YEARS
                );

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

    private static void constrainTurnover(Parameter parameter) {
        double value = parameter.getParameterValue(0);
        if (value < 0.0 || value >= 1.0) {
            throw new IllegalArgumentException(
                    "BirthDeath turnover must be in [0, 1)."
            );
        }
        parameter.addBounds(new Parameter.DefaultBounds(
                1.0,
                0.0,
                parameter.getDimension()
        ));
    }
}
