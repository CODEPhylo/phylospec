package tiles.trees;

import dr.evolution.util.Taxa;
import dr.evolution.util.Units;
import dr.evomodel.speciation.BirthDeathGernhard08Model;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.evomodel.tree.DefaultTreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.model.BeastXTreeDistribution;

import java.util.IdentityHashMap;

public class BirthDeathTile extends GeneratorTile<
        BeastXTreeDistribution<SpeciationLikelihood>,
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
    public BeastXTreeDistribution<SpeciationLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends PositiveReal> diversificationRate =
                this.diversificationRateInput.apply(beastState, indexVariables);

        RealScalar<? extends NonNegativeReal> turnover =
                this.turnoverInput.apply(beastState, indexVariables);

        validateTurnover(turnover);

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
                        : toParameter(samplingProbability);

        BirthDeathGernhard08Model birthDeathModel =
                new BirthDeathGernhard08Model(
                        toParameter(diversificationRate),
                        toParameter(turnover),
                        samplingProbabilityParameter,
                        BirthDeathGernhard08Model.TreeType.LABELED,
                        Units.Type.YEARS
                );

        SpeciationLikelihood likelihood =
                new SpeciationLikelihood(
                        defaultTreeModel,
                        birthDeathModel,
                        "birthDeathPrior"
                );

        return new BeastXTreeDistribution<>(
                likelihood,
                defaultTreeModel,
                treeModel -> {
                    // SpeciationLikelihood receives the tree in its constructor.
                }
        );
    }

    private static void validateTurnover(RealScalar<? extends NonNegativeReal> turnover) {
        if (turnover instanceof BeastXRealScalarParam<?>) {
            return;
        }

        double value =
                turnover.get();

        if (value > 1.0) {
            throw new IllegalArgumentException(
                    "BirthDeath turnover must be in [0, 1]. " +
                            "Use a UnitInterval prior such as turnover~Beta(alpha=2.0, beta=5.0)."
            );
        }
    }

    private static Parameter toParameter(RealScalar<?> scalar) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return beastXScalar.getParameter();
        }

        return new Parameter.Default(scalar.get());
    }
}