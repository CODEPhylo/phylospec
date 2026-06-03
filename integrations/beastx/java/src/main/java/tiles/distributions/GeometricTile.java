package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.model.Parameter;
import dr.math.UnivariateFunction;
import dr.math.distributions.Distribution;
import dr.util.Attribute;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXIntScalarParam;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;

import java.util.IdentityHashMap;


public class GeometricTile extends GeneratorTile<
        BoundDistribution<BeastXIntScalarParam<NonNegativeInt>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Geometric";
    }

    GeneratorTileInput<RealScalar<UnitInterval>, BeastXState> pInput =
            new GeneratorTileInput<>("p");

    @Override
    public BoundDistribution<BeastXIntScalarParam<NonNegativeInt>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<UnitInterval> p =
                this.pInput.apply(beastState, indexVariables);

        validateProbability(p.get());

        Parameter pParameter =
                toParameter(p);

        DistributionLikelihood likelihood =
                new DistributionLikelihood(
                        new GeometricCountDistribution(pParameter)
                );

        Parameter.Default defaultParameter =
                new Parameter.Default(0.0);

        defaultParameter.addBounds(
                0.0,
                Double.POSITIVE_INFINITY
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
        if (p <= 0.0 || p > 1.0) {
            throw new TileApplicationError(
                    "Geometric probability p must be in (0, 1].",
                    "Use a probability greater than 0.0 and less than or equal to 1.0."
            );
        }
    }

    private static final class GeometricCountDistribution implements Distribution {

        private final Parameter pParameter;

        private GeometricCountDistribution(Parameter pParameter) {
            this.pParameter = pParameter;
        }

        @Override
        public double pdf(double x) {
            return Math.exp(logPdf(x));
        }

        @Override
        public double logPdf(double x) {
            int failures =
                    asIntegerCount(x);

            if (failures < 0) {
                return Double.NEGATIVE_INFINITY;
            }

            double p =
                    p();

            if (p == 1.0) {
                return failures == 0 ? 0.0 : Double.NEGATIVE_INFINITY;
            }

            return Math.log(p) + failures * Math.log1p(-p);
        }

        @Override
        public double cdf(double x) {
            int failures =
                    (int) Math.floor(x);

            if (failures < 0) {
                return 0.0;
            }

            double p =
                    p();

            if (p == 1.0) {
                return 1.0;
            }

            return 1.0 - Math.pow(1.0 - p, failures + 1.0);
        }

        @Override
        public double quantile(double y) {
            if (y <= 0.0) {
                return 0.0;
            }

            if (y >= 1.0) {
                return Double.POSITIVE_INFINITY;
            }

            double p =
                    p();

            if (p == 1.0) {
                return 0.0;
            }

            double failures =
                    Math.ceil(Math.log1p(-y) / Math.log1p(-p)) - 1.0;

            return Math.max(0.0, failures);
        }

        @Override
        public double mean() {
            double p =
                    p();

            return (1.0 - p) / p;
        }

        @Override
        public double variance() {
            double p =
                    p();

            return (1.0 - p) / (p * p);
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
                    return Double.POSITIVE_INFINITY;
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