package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.model.Parameter;
import dr.math.UnivariateFunction;
import dr.math.distributions.Distribution;
import dr.util.Attribute;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.domain.Real;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXIntScalarParam;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;
import java.util.List;

public class BernoulliTile extends GeneratorTile<
        BoundDistribution<BeastXIntScalarParam<NonNegativeInt>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Bernoulli";
    }

    GeneratorTileInput<RealScalar<? extends Real>, BeastXState> pInput =
            new GeneratorTileInput<>("p");

    @Override
    public BoundDistribution<BeastXIntScalarParam<NonNegativeInt>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends Real> p =
                this.pInput.apply(beastState, indexVariables);

        validateProbability(
                p.get(),
                this.getRootNode()
        );

        Parameter pParameter =
                toParameter(p);

        DistributionLikelihood likelihood =
                new DistributionLikelihood(
                        new BernoulliDistribution(pParameter)
                );

        Parameter.Default defaultParameter =
                new Parameter.Default(p.get() >= 0.5 ? 1.0 : 0.0);

        defaultParameter.addBounds(
                0.0,
                1.0
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

    private static Parameter toParameter(RealScalar<?> scalar) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return beastXScalar.getParameter();
        }

        return new Parameter.Default(scalar.get());
    }

    private static void validateProbability(
            double p,
            Object rootNode
    ) {
        if (p < 0.0 || p > 1.0) {
            throw new TileApplicationError(
                    rootNode instanceof org.phylospec.ast.AstNode node ? node : null,
                    "Bernoulli probability p must be in [0, 1].",
                    "Use a probability value between 0.0 and 1.0.",
                    List.of("NonNegativeInt x ~ Bernoulli(p=0.25)")
            );
        }
    }

    private static final class BernoulliDistribution implements Distribution {

        private final Parameter pParameter;

        private BernoulliDistribution(Parameter pParameter) {
            this.pParameter = pParameter;
        }

        @Override
        public double pdf(double x) {
            return Math.exp(logPdf(x));
        }

        @Override
        public double logPdf(double x) {
            int value =
                    asIntegerValue(x);

            if (value != 0 && value != 1) {
                return Double.NEGATIVE_INFINITY;
            }

            double p =
                    p();

            if (value == 1) {
                return p == 0.0 ? Double.NEGATIVE_INFINITY : Math.log(p);
            }

            return p == 1.0 ? Double.NEGATIVE_INFINITY : Math.log1p(-p);
        }

        @Override
        public double cdf(double x) {
            if (x < 0.0) {
                return 0.0;
            }

            if (x < 1.0) {
                return 1.0 - p();
            }

            return 1.0;
        }

        @Override
        public double quantile(double y) {
            if (y <= 1.0 - p()) {
                return 0.0;
            }

            return 1.0;
        }

        @Override
        public double mean() {
            return p();
        }

        @Override
        public double variance() {
            double p =
                    p();

            return p * (1.0 - p);
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
                    return 1.0;
                }
            };
        }

        private double p() {
            double value =
                    pParameter.getParameterValue(0);

            if (value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException(
                        "Bernoulli probability p must be in [0, 1]."
                );
            }

            return value;
        }

        private static int asIntegerValue(double x) {
            int rounded =
                    (int) Math.rint(x);

            if (Math.abs(x - rounded) > 1e-9) {
                return -1;
            }

            return rounded;
        }
    }
}