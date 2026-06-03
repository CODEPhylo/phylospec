package tiles.trees;

import dr.evolution.util.Taxa;
import dr.evolution.util.Units;
import dr.evomodel.speciation.BirthDeathSerialSamplingModel;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.evomodel.tree.DefaultTreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXDerivedScalarParameter;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.model.BeastXTreeDistribution;

import java.util.IdentityHashMap;
import java.util.List;

public class FossilizedBirthDeathTile extends GeneratorTile<
        BeastXTreeDistribution<SpeciationLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "FossilizedBirthDeath";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> speciationRateInput =
            new GeneratorTileInput<>("speciationRate", false);

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> extinctionRateInput =
            new GeneratorTileInput<>("extinctionRate", false);

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> diversificationRateInput =
            new GeneratorTileInput<>("diversificationRate", false);

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> turnoverInput =
            new GeneratorTileInput<>("turnover", false);

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> serialSamplingRateInput =
            new GeneratorTileInput<>("serialSamplingRate");

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
        RealScalar<? extends PositiveReal> speciationRate =
                this.speciationRateInput.apply(beastState, indexVariables);

        RealScalar<? extends PositiveReal> extinctionRate =
                this.extinctionRateInput.apply(beastState, indexVariables);

        RealScalar<? extends PositiveReal> diversificationRate =
                this.diversificationRateInput.apply(beastState, indexVariables);

        RealScalar<? extends PositiveReal> turnover =
                this.turnoverInput.apply(beastState, indexVariables);

        RealScalar<? extends PositiveReal> serialSamplingRate =
                this.serialSamplingRateInput.apply(beastState, indexVariables);

        RealScalar<UnitInterval> samplingProbability =
                this.samplingProbabilityInput.apply(beastState, indexVariables);

        RealScalar<? extends NonNegativeReal> rootAge =
                this.rootAgeInput.apply(beastState, indexVariables);

        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        Parameter birthRateParameter;
        Parameter deathRateParameter;

        boolean usesBirthDeathRates =
                speciationRate != null || extinctionRate != null;

        boolean usesDiversificationTurnover =
                diversificationRate != null || turnover != null;

        if (usesBirthDeathRates && usesDiversificationTurnover) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "FossilizedBirthDeath cannot mix speciation/extinction and diversification/turnover parameterizations.",
                    "Use either speciationRate/extinctionRate or diversificationRate/turnover.",
                    List.of(
                            "FossilizedBirthDeath(speciationRate=1.0, extinctionRate=0.2, serialSamplingRate=0.1, taxa=taxa)",
                            "FossilizedBirthDeath(diversificationRate=0.8, turnover=0.2, serialSamplingRate=0.1, taxa=taxa)"
                    )
            );
        }

        if (usesBirthDeathRates) {
            if (speciationRate == null || extinctionRate == null) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "FossilizedBirthDeath requires both speciationRate and extinctionRate.",
                        "Provide both arguments when using the speciation/extinction parameterization.",
                        List.of("Tree tree ~ FossilizedBirthDeath(speciationRate=1.0, extinctionRate=0.2, serialSamplingRate=0.1, taxa=taxa)")
                );
            }

            birthRateParameter =
                    toParameter(speciationRate);

            deathRateParameter =
                    toParameter(extinctionRate);
        } else if (usesDiversificationTurnover) {
            if (diversificationRate == null || turnover == null) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "FossilizedBirthDeath requires both diversificationRate and turnover.",
                        "Provide both arguments when using the diversification/turnover parameterization.",
                        List.of("Tree tree ~ FossilizedBirthDeath(diversificationRate=0.8, turnover=0.2, serialSamplingRate=0.1, taxa=taxa)")
                );
            }

            Parameter diversificationRateParameter =
                    toParameter(diversificationRate);

            Parameter turnoverParameter =
                    toParameter(turnover);

            validateTurnover(turnoverParameter.getParameterValue(0));

            birthRateParameter =
                    new BeastXDerivedScalarParameter(
                            "fossilizedBirthDeath.birthRate",
                            () -> {
                                double turnoverValue =
                                        turnoverParameter.getParameterValue(0);

                                validateTurnover(turnoverValue);

                                return diversificationRateParameter.getParameterValue(0)
                                        / (1.0 - turnoverValue);
                            },
                            diversificationRateParameter,
                            turnoverParameter
                    );

            deathRateParameter =
                    new BeastXDerivedScalarParameter(
                            "fossilizedBirthDeath.deathRate",
                            () -> {
                                double turnoverValue =
                                        turnoverParameter.getParameterValue(0);

                                validateTurnover(turnoverValue);

                                double birthRate =
                                        diversificationRateParameter.getParameterValue(0)
                                                / (1.0 - turnoverValue);

                                return birthRate * turnoverValue;
                            },
                            diversificationRateParameter,
                            turnoverParameter
                    );
        } else {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "FossilizedBirthDeath requires a birth/death parameterization.",
                    "Use either speciationRate/extinctionRate or diversificationRate/turnover.",
                    List.of(
                            "Tree tree ~ FossilizedBirthDeath(speciationRate=1.0, extinctionRate=0.2, serialSamplingRate=0.1, taxa=taxa)",
                            "Tree tree ~ FossilizedBirthDeath(diversificationRate=0.8, turnover=0.2, serialSamplingRate=0.1, taxa=taxa)"
                    )
            );
        }

        DefaultTreeModel defaultTreeModel =
                new DefaultTreeModel(
                        "tree",
                        InitialTreeBuilder.balancedTree(taxa, "FossilizedBirthDeath", rootAge)
                );

        Parameter samplingProbabilityParameter =
                samplingProbability == null
                        ? new Parameter.Default(1.0)
                        : toParameter(samplingProbability);

        BirthDeathSerialSamplingModel fbdModel =
                new BirthDeathSerialSamplingModel(
                        "fossilizedBirthDeath",
                        birthRateParameter,
                        deathRateParameter,
                        toParameter(serialSamplingRate),
                        samplingProbabilityParameter,
                        false,
                        new Parameter.Default(0.0),
                        false,
                        null,
                        Units.Type.YEARS
                );

        SpeciationLikelihood likelihood =
                new SpeciationLikelihood(
                        defaultTreeModel,
                        fbdModel,
                        "fossilizedBirthDeathPrior"
                );

        return new BeastXTreeDistribution<>(
                likelihood,
                defaultTreeModel,
                treeModel -> {
                    // SpeciationLikelihood receives the tree in its constructor.
                }
        );
    }

    private static void validateTurnover(double turnoverValue) {
        if (turnoverValue < 0.0 || turnoverValue >= 1.0) {
            throw new IllegalArgumentException(
                    "FossilizedBirthDeath turnover must be in [0, 1)."
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