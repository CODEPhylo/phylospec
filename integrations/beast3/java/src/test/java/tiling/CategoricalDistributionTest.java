package tiling;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.core.BEASTObject;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.type.Simplex;
import beast.base.util.Randomizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CategoricalDistributionTest {
    private static final double TOLERANCE = 1e-12;

    @Test
    void numericalMethodsUseOneBasedCategories() {
        CategoricalDistribution distribution = distribution(new SimplexParam(new double[] {0.2, 0.3, 0.5}));

        assertTrue(distribution.isIntegerDistribution());
        assertEquals(1, distribution.getLowerBoundOfParameter());
        assertEquals(3, distribution.getUpperBoundOfParameter());
        assertEquals(1.0, distribution.getLower());
        assertEquals(3.0, distribution.getUpper());
        assertTrue(distribution.lowerInclusive());
        assertTrue(distribution.upperInclusive());
        assertEquals(2.3, distribution.getMean(), TOLERANCE);
        assertEquals(3, distribution.mostLikelyCategory());
        assertTrue(distribution.getConditions().isEmpty());
        for (int category = 1; category <= 3; category++) {
            double probability = new double[] {0.2, 0.3, 0.5}[category - 1];
            assertEquals(probability, distribution.density(category), TOLERANCE);
            assertEquals(Math.log(probability), distribution.logDensity(category), TOLERANCE);
        }
        for (double category : new double[] {-1.0, 0.0, 1.5, 4.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(0.0, distribution.density(category), 0.0);
            assertEquals(Double.NEGATIVE_INFINITY, distribution.logDensity(category));
        }
        assertEquals(0.0, distribution.cumulativeProbability(0.9), 0.0);
        assertEquals(0.2, distribution.cumulativeProbability(1.9), TOLERANCE);
        assertEquals(0.5, distribution.cumulativeProbability(2.0), TOLERANCE);
        assertEquals(1.0, distribution.cumulativeProbability(3.0), 0.0);
        assertEquals(1, distribution.inverseCumulativeProbability(0.0));
        assertEquals(1, distribution.inverseCumulativeProbability(0.2));
        assertEquals(2, distribution.inverseCumulativeProbability(Math.nextUp(0.2)));
        assertEquals(2, distribution.inverseCumulativeProbability(0.5));
        assertEquals(3, distribution.inverseCumulativeProbability(Math.nextUp(0.5)));
        assertEquals(3, distribution.inverseCumulativeProbability(1.0));
    }

    @Test
    void boundParameterAndConditionMetadataRetainTheirIdentities() {
        SimplexParam probabilities = new SimplexParam(new double[] {0.2, 0.3, 0.5});
        probabilities.setID("probabilities");
        IntScalarParam<PositiveInt> category = new IntScalarParam<>(2, PositiveInt.INSTANCE);
        category.setID("category");
        CategoricalDistribution distribution = new CategoricalDistribution();
        distribution.setID("categoryPrior");
        distribution.initByName("probabilities", probabilities, "param", category);

        assertSame(probabilities, distribution.probabilitiesInput.get());
        assertSame(category, distribution.paramInput.get());
        assertEquals(List.of("probabilities"), distribution.getConditions());
        assertEquals(List.of("category"), distribution.getArguments());
        assertEquals(Math.log(0.3), distribution.calculateLogP(), TOLERANCE);
        category.set(3);
        assertEquals(Math.log(0.5), distribution.calculateLogP(), TOLERANCE);
    }

    private static Stream<Arguments> invalidProbabilities() {
        return Stream.of(
                Arguments.of((Object) new double[] {}),
                Arguments.of((Object) new double[] {0.0, 0.0}),
                Arguments.of((Object) new double[] {-0.1, 0.6, 0.5}),
                Arguments.of((Object) new double[] {1.1, 0.0}),
                Arguments.of((Object) new double[] {Double.NaN, 1.0}),
                Arguments.of((Object) new double[] {Double.POSITIVE_INFINITY, 0.0}),
                Arguments.of((Object) new double[] {0.2, 0.3}),
                Arguments.of((Object) new double[] {0.2, 0.8 + 2e-10}));
    }

    @ParameterizedTest
    @MethodSource("invalidProbabilities")
    void rejectsMalformedProbabilitiesBeforeUse(double[] probabilities) {
        assertThrows(IllegalArgumentException.class, () -> distribution(new MutableSimplex(probabilities)));
    }

    @Test
    void probabilitiesAreRequired() {
        CategoricalDistribution distribution = new CategoricalDistribution();
        assertThrows(IllegalArgumentException.class, distribution::initAndValidate);
    }

    @Test
    void singleCategoryIsDeterministic() {
        CategoricalDistribution distribution = distribution(new SimplexParam(new double[] {1.0}));
        assertEquals(1, distribution.mostLikelyCategory());
        assertEquals(1.0, distribution.getMean(), 0.0);
        assertEquals(0.0, distribution.logDensity(1), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, distribution.logDensity(2));
        assertEquals(1, distribution.inverseCumulativeProbability(0.0));
        assertEquals(1, distribution.inverseCumulativeProbability(1.0));
        assertEquals(List.of(1), distribution.sample());
    }

    @Test
    void seededSamplingIsReproducibleAndSkipsZeroMassCategories() {
        CategoricalDistribution distribution = distribution(new SimplexParam(new double[] {0.0, 0.25, 0.0, 0.75, 0.0}));
        assertEquals(1, distribution.inverseCumulativeProbability(0.0));
        assertEquals(5, distribution.inverseCumulativeProbability(1.0));
        assertEquals(2, distribution.inverseCumulativeProbability(0.1));
        assertEquals(4, distribution.inverseCumulativeProbability(0.9));

        Randomizer.setSeed(61027);
        List<Integer> first = samples(distribution, 64);
        Randomizer.setSeed(61027);
        List<Integer> second = samples(distribution, 64);

        assertEquals(first, second);
        assertTrue(first.stream().allMatch(Set.of(2, 4)::contains));
    }

    @Test
    void numericalMethodsReadCurrentProbabilities() {
        SimplexParam probabilities = new SimplexParam(new double[] {0.2, 0.3, 0.5});
        CategoricalDistribution distribution = distribution(probabilities);
        probabilities.set(0, 0.6);
        probabilities.set(1, 0.3);
        probabilities.set(2, 0.1);

        assertSame(probabilities, distribution.probabilitiesInput.get());
        assertEquals(Math.log(0.6), distribution.logDensity(1), TOLERANCE);
        assertEquals(0.9, distribution.cumulativeProbability(2), TOLERANCE);
        assertEquals(1.5, distribution.getMean(), TOLERANCE);
        assertEquals(1, distribution.mostLikelyCategory());
        assertEquals(1, distribution.inverseCumulativeProbability(0.5));
    }

    @Test
    void categoryCountCannotChangeAfterInitialization() {
        MutableSimplex probabilities = new MutableSimplex(new double[] {0.25, 0.75});
        CategoricalDistribution distribution = distribution(probabilities);
        probabilities.values = new double[] {0.2, 0.3, 0.5};

        assertThrows(IllegalArgumentException.class, () -> distribution.logDensity(1));
        assertThrows(IllegalArgumentException.class, distribution::sample);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
    void quantileRequiresAProbability(double probability) {
        CategoricalDistribution distribution = distribution(new SimplexParam(new double[] {0.2, 0.3, 0.5}));
        assertThrows(IllegalArgumentException.class, () -> distribution.inverseCumulativeProbability(probability));
    }

    @Test
    void normalizationToleranceDoesNotRenormalizeTheInput() {
        double second = 0.8 + 5e-11;
        CategoricalDistribution distribution = distribution(new MutableSimplex(new double[] {0.2, second}));
        assertEquals(second, distribution.density(2), 1e-15);
        assertEquals(Math.log(second), distribution.logDensity(2), 1e-15);
    }

    private static CategoricalDistribution distribution(Simplex probabilities) {
        CategoricalDistribution distribution = new CategoricalDistribution();
        distribution.probabilitiesInput.setValue(probabilities, distribution);
        distribution.initAndValidate();
        return distribution;
    }

    private static List<Integer> samples(CategoricalDistribution distribution, int count) {
        List<Integer> samples = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<Integer> sample = distribution.sample();
            assertEquals(1, sample.size());
            samples.add(sample.getFirst());
        }
        return samples;
    }

    /** Allows malformed values to reach the distribution's own input validation. */
    public static final class MutableSimplex extends BEASTObject implements Simplex {
        private double[] values;

        MutableSimplex(double[] values) {
            this.values = values.clone();
        }

        @Override
        public void initAndValidate() {}

        @Override
        public double get(int index) {
            return values[index];
        }

        @Override
        public List<Double> getElements() {
            return Arrays.stream(values).boxed().toList();
        }

        @Override
        public UnitInterval getDomain() {
            return UnitInterval.INSTANCE;
        }
    }
}
