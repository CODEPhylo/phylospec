package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.model.Parameter;
import dr.math.UnivariateFunction;
import dr.math.distributions.Distribution;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveInt;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.Simplex;
import tiling.params.BeastXIntScalarParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;
import tiling.params.BeastXParameters;
import tiling.model.ParameterAttribute;

import java.util.IdentityHashMap;

public class CategoricalTile extends GeneratorTile<
        BoundDistribution<BeastXIntScalarParam<PositiveInt>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Categorical";
    }

    GeneratorTileInput<Simplex, BeastXState> probabilitiesInput =
            new GeneratorTileInput<>("probabilities");

    @Override
    public BoundDistribution<BeastXIntScalarParam<PositiveInt>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Simplex probabilities =
                this.probabilitiesInput.apply(beastState, indexVariables);

        int categoryCount =
                Math.toIntExact(probabilities.size());

        validateProbabilities(probabilities);

        Parameter probabilitiesParameter =
                BeastXParameters.toParameter(probabilities);

        DistributionLikelihood likelihood =
                new DistributionLikelihood(
                        new CategoricalDistribution(probabilitiesParameter)
                );

        Parameter.Default defaultParameter =
                new Parameter.Default((double) mostLikelyCategory(probabilities));

        defaultParameter.addBounds(
                1.0,
                categoryCount
        );

        BeastXIntScalarParam<PositiveInt> defaultState =
                new BeastXIntScalarParam<>(
                        defaultParameter,
                        PositiveInt.INSTANCE
                );

        return new BoundDistribution<>(
                likelihood,
                defaultState,
                state -> likelihood.addData(new ParameterAttribute(state.getParameter()))
        );
    }

    private static void validateProbabilities(Simplex probabilities) {
        if (probabilities.size() == 0) {
            throw new TileApplicationError(
                    "Categorical probabilities cannot be empty.",
                    "Use a non-empty Simplex, for example probabilities=[0.25, 0.75]."
            );
        }

        double sum =
                0.0;

        for (int i = 0; i < probabilities.size(); i++) {
            double value =
                    probabilities.get(i);

            if (!Double.isFinite(value) || value < 0.0) {
                throw new TileApplicationError(
                        "Categorical probabilities must be finite and non-negative.",
                        "Use a valid Simplex, for example probabilities=[0.25, 0.75]."
                );
            }

            sum += value;
        }

        if (Math.abs(sum - 1.0) > 1e-6) {
            throw new TileApplicationError(
                    "Categorical probabilities must sum to 1.",
                    "Use a valid Simplex, for example probabilities=[0.25, 0.75]."
            );
        }
    }

    private static int mostLikelyCategory(Simplex probabilities) {
        int bestCategory =
                1;

        double bestProbability =
                probabilities.get(0);

        for (int i = 1; i < probabilities.size(); i++) {
            double probability =
                    probabilities.get(i);

            if (probability > bestProbability) {
                bestProbability =
                        probability;

                bestCategory =
                        i + 1;
            }
        }

        return bestCategory;
    }

    private static final class CategoricalDistribution implements Distribution {

        private final Parameter probabilities;

        private CategoricalDistribution(Parameter probabilities) {
            this.probabilities = probabilities;
        }

        @Override
        public double pdf(double x) {
            return Math.exp(logPdf(x));
        }

        @Override
        public double logPdf(double x) {
            int category =
                    asIntegerCategory(x);

            if (category < 1 || category > probabilities.getDimension()) {
                return Double.NEGATIVE_INFINITY;
            }

            double p =
                    probability(category);

            return p == 0.0 ? Double.NEGATIVE_INFINITY : Math.log(p);
        }

        @Override
        public double cdf(double x) {
            int upper =
                    (int) Math.floor(x);

            if (upper < 1) {
                return 0.0;
            }

            if (upper >= probabilities.getDimension()) {
                return 1.0;
            }

            double cumulative =
                    0.0;

            for (int category = 1; category <= upper; category++) {
                cumulative += probability(category);
            }

            return Math.min(1.0, cumulative);
        }

        @Override
        public double quantile(double y) {
            if (y <= 0.0) {
                return 1.0;
            }

            if (y >= 1.0) {
                return probabilities.getDimension();
            }

            double cumulative =
                    0.0;

            for (int category = 1; category <= probabilities.getDimension(); category++) {
                cumulative += probability(category);

                if (cumulative >= y) {
                    return category;
                }
            }

            return probabilities.getDimension();
        }

        @Override
        public double mean() {
            double mean =
                    0.0;

            for (int category = 1; category <= probabilities.getDimension(); category++) {
                mean += category * probability(category);
            }

            return mean;
        }

        @Override
        public double variance() {
            double mean =
                    mean();

            double secondMoment =
                    0.0;

            for (int category = 1; category <= probabilities.getDimension(); category++) {
                secondMoment += category * category * probability(category);
            }

            return secondMoment - mean * mean;
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
                    return 1.0;
                }

                @Override
                public double getUpperBound() {
                    return probabilities.getDimension();
                }
            };
        }

        private double probability(int category) {
            double value =
                    probabilities.getParameterValue(category - 1);

            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException(
                        "Categorical probabilities must be finite and non-negative."
                );
            }

            return value;
        }

        private static int asIntegerCategory(double x) {
            int rounded =
                    (int) Math.rint(x);

            if (Math.abs(x - rounded) > 1e-9) {
                return -1;
            }

            return rounded;
        }
    }
}
