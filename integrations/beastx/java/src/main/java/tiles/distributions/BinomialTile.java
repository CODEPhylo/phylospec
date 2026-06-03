package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.model.Parameter;
import dr.math.Binomial;
import dr.math.UnivariateFunction;
import dr.math.distributions.Distribution;
import dr.util.Attribute;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.IntScalar;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXIntScalarParam;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;

import java.util.IdentityHashMap;
import java.util.List;

public class BinomialTile extends GeneratorTile<
        BoundDistribution<BeastXIntScalarParam<NonNegativeInt>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Binomial";
    }

    GeneratorTileInput<IntScalar<NonNegativeInt>, BeastXState> numTrialsInput =
            new GeneratorTileInput<>("numTrials");

    GeneratorTileInput<RealScalar<UnitInterval>, BeastXState> pInput =
            new GeneratorTileInput<>("p");

    @Override
    public BoundDistribution<BeastXIntScalarParam<NonNegativeInt>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        IntScalar<NonNegativeInt> numTrials =
                this.numTrialsInput.apply(beastState, indexVariables);

        RealScalar<UnitInterval> p =
                this.pInput.apply(beastState, indexVariables);

        int trialCount =
                numTrials.get();

        if (trialCount < 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Binomial numTrials must be non-negative.",
                    "Use numTrials >= 0.",
                    List.of("NonNegativeInt x ~ Binomial(numTrials=10, p=0.25)")
            );
        }

        validateProbability(p.get());

        Parameter pParameter =
                toParameter(p);

        DistributionLikelihood likelihood =
                new DistributionLikelihood(
                        new BinomialCountDistribution(
                                trialCount,
                                pParameter
                        )
                );

        Parameter.Default defaultParameter =
                new Parameter.Default(Math.round(trialCount * p.get()));

        defaultParameter.addBounds(
                0.0,
                trialCount
        );

        BeastXIntScalarParam<NonNegativeInt> defaultState =
                new BeastXIntScalarParam<>(
                        defaultParameter,
                        NonNegativeInt.INSTANCE
                );

        return new BoundDistribution<>(
                likelihood,
                defaultState,
                state -> likelihood.addData(new Attribute.Default<>(
                        state.getParameter().getParameterName(),
                        new double[]{state.getParameter().getParameterValue(0)}
                ))
        );
    }

    private static Parameter toParameter(RealScalar<UnitInterval> scalar) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return beastXScalar.getParameter();
        }

        return new Parameter.Default(scalar.get());
    }

    private static void validateProbability(double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Binomial probability p must be in [0, 1].");
        }
    }

    private static final class BinomialCountDistribution implements Distribution {

        private final int numTrials;
        private final Parameter pParameter;

        private BinomialCountDistribution(
                int numTrials,
                Parameter pParameter
        ) {
            this.numTrials = numTrials;
            this.pParameter = pParameter;
        }

        @Override
        public double pdf(double x) {
            return Math.exp(logPdf(x));
        }

        @Override
        public double logPdf(double x) {
            int k =
                    asIntegerCount(x);

            if (k < 0 || k > numTrials) {
                return Double.NEGATIVE_INFINITY;
            }

            double p =
                    p();

            if (p == 0.0) {
                return k == 0 ? 0.0 : Double.NEGATIVE_INFINITY;
            }

            if (p == 1.0) {
                return k == numTrials ? 0.0 : Double.NEGATIVE_INFINITY;
            }

            return Binomial.logChoose(numTrials, k)
                    + k * Math.log(p)
                    + (numTrials - k) * Math.log1p(-p);
        }

        @Override
        public double cdf(double x) {
            int upper =
                    (int) Math.floor(x);

            if (upper < 0) {
                return 0.0;
            }

            if (upper >= numTrials) {
                return 1.0;
            }

            double sum =
                    0.0;

            for (int k = 0; k <= upper; k++) {
                sum += Math.exp(logPdf(k));
            }

            return Math.min(1.0, sum);
        }

        @Override
        public double quantile(double y) {
            if (y <= 0.0) {
                return 0.0;
            }

            if (y >= 1.0) {
                return numTrials;
            }

            double cumulative =
                    0.0;

            for (int k = 0; k <= numTrials; k++) {
                cumulative += Math.exp(logPdf(k));

                if (cumulative >= y) {
                    return k;
                }
            }

            return numTrials;
        }

        @Override
        public double mean() {
            return numTrials * p();
        }

        @Override
        public double variance() {
            double p =
                    p();

            return numTrials * p * (1.0 - p);
        }

        @Override
        public UnivariateFunction getProbabilityDensityFunction() {
            return new UnivariateFunction() {
                @Override
                public double evaluate(double argument) {
                    return pdf(argument);
                }

                @Override
                public double getLowerBound() {
                    return 0.0;
                }

                @Override
                public double getUpperBound() {
                    return numTrials;
                }
            };
        }

        private double p() {
            double value =
                    pParameter.getParameterValue(0);

            validateProbability(value);

            return value;
        }

        private static int asIntegerCount(double x) {
            int rounded =
                    (int) Math.rint(x);

            if (Math.abs(x - rounded) > 1e-9) {
                return -1;
            }

            return rounded;
        }
    }
}