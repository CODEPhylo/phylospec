package tiling;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.core.BEASTObject;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beast.base.util.Randomizer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BinomialDistributionTest {
    private static final double TOLERANCE = 1e-12;

    @Test
    void numericalMethodsMatchTheThreeTrialDistribution() {
        BinomialDistribution distribution = distribution(3, probability(0.25));

        assertTrue(distribution.isIntegerDistribution());
        assertBounds(distribution, 3);
        assertTrue(distribution.getConditions().isEmpty());
        assertEquals(0.75, distribution.getMean(), TOLERANCE);
        assertEquals(1, distribution.mostLikelyCount());
        double[] masses = {27.0 / 64, 27.0 / 64, 9.0 / 64, 1.0 / 64};
        for (int count = 0; count <= 3; count++) {
            assertEquals(masses[count], distribution.density(count), TOLERANCE);
            assertEquals(Math.log(masses[count]), distribution.logDensity(count), TOLERANCE);
        }
        assertEquals(0.0, distribution.cumulativeProbability(-0.25), 0.0);
        assertEquals(0.0, distribution.cumulativeProbability(Double.NEGATIVE_INFINITY), 0.0);
        assertEquals(27.0 / 64, distribution.cumulativeProbability(0.9), TOLERANCE);
        assertEquals(54.0 / 64, distribution.cumulativeProbability(1.0), TOLERANCE);
        assertEquals(54.0 / 64, distribution.cumulativeProbability(1.9), TOLERANCE);
        assertEquals(63.0 / 64, distribution.cumulativeProbability(2.0), TOLERANCE);
        assertEquals(1.0, distribution.cumulativeProbability(3.0), 0.0);
        assertEquals(1.0, distribution.cumulativeProbability(Double.POSITIVE_INFINITY), 0.0);
        assertThrows(IllegalArgumentException.class, () -> distribution.cumulativeProbability(Double.NaN));
        assertEquals(0, distribution.inverseCumulativeProbability(0.0));
        assertEquals(0, distribution.inverseCumulativeProbability(0.4));
        assertEquals(1, distribution.inverseCumulativeProbability(0.5));
        assertEquals(2, distribution.inverseCumulativeProbability(0.9));
        assertEquals(3, distribution.inverseCumulativeProbability(0.99));
        assertEquals(3, distribution.inverseCumulativeProbability(1.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, -0.25, 0.5, 2.5, 4.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void nonIntegerOrUnsupportedCountsHaveZeroMass(double count) {
        BinomialDistribution distribution = distribution(3, probability(0.25));
        assertEquals(0.0, distribution.density(count), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, distribution.logDensity(count));
    }

    @Test
    void boundParameterAndConditionMetadataRetainTheirIdentities() {
        RealScalarParam<UnitInterval> probability = probability(0.25);
        probability.setID("successProbability");
        IntScalarParam<NonNegativeInt> count = new IntScalarParam<>(1, NonNegativeInt.INSTANCE);
        count.setID("count");
        BinomialDistribution distribution = new BinomialDistribution();
        distribution.setID("countPrior");
        distribution.initByName("numTrials", 3, "p", probability, "param", count);

        assertSame(probability, distribution.pInput.get());
        assertSame(count, distribution.paramInput.get());
        assertEquals(List.of("successProbability"), distribution.getConditions());
        assertEquals(List.of("count"), distribution.getArguments());
        assertEquals(Math.log(27.0 / 64), distribution.calculateLogP(), TOLERANCE);
        count.set(2);
        assertEquals(Math.log(9.0 / 64), distribution.calculateLogP(), TOLERANCE);
        probability.set(0.75);
        assertEquals(Math.log(27.0 / 64), distribution.calculateLogP(), TOLERANCE);
    }

    @Test
    void observedCountBeyondTheTrialCountIsLegalDataWithZeroProbability() {
        IntScalarParam<NonNegativeInt> count = new IntScalarParam<>(4, NonNegativeInt.INSTANCE);
        BinomialDistribution distribution = new BinomialDistribution();

        assertDoesNotThrow(() -> distribution.initByName("numTrials", 3, "p", probability(0.25), "param", count));
        assertSame(count, distribution.paramInput.get());
        assertEquals(Double.NEGATIVE_INFINITY, distribution.calculateLogP());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.25, 1.0})
    void zeroTrialsAlwaysProducesZero(double probability) {
        BinomialDistribution distribution = distribution(0, probability(probability));

        assertBounds(distribution, 0);
        assertEquals(0, distribution.mostLikelyCount());
        assertEquals(0.0, distribution.getMean(), 0.0);
        assertEquals(1.0, distribution.density(0), 0.0);
        assertEquals(0.0, distribution.logDensity(0), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, distribution.logDensity(1));
        assertEquals(0.0, distribution.cumulativeProbability(-0.25), 0.0);
        assertEquals(1.0, distribution.cumulativeProbability(0), 0.0);
        assertEquals(0, distribution.inverseCumulativeProbability(0.0));
        assertEquals(0, distribution.inverseCumulativeProbability(0.5));
        assertEquals(0, distribution.inverseCumulativeProbability(1.0));
        assertEquals(List.of(0), distribution.sample());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0})
    void endpointProbabilitiesAreDeterministicWithStableParameterBounds(double probability) {
        BinomialDistribution distribution = distribution(3, probability(probability));
        int supportedCount = (int) (3 * probability);

        assertBounds(distribution, 3);
        assertEquals(supportedCount, distribution.mostLikelyCount());
        assertEquals((double) supportedCount, distribution.getMean(), 0.0);
        for (int count = 0; count <= 3; count++) {
            assertEquals(count == supportedCount ? 1.0 : 0.0, distribution.density(count), 0.0);
            assertEquals(count == supportedCount ? 0.0 : Double.NEGATIVE_INFINITY, distribution.logDensity(count));
        }
        for (double quantile : new double[] {0.0, 0.5, 1.0}) {
            assertEquals(supportedCount, distribution.inverseCumulativeProbability(quantile));
        }
        assertEquals(List.of(supportedCount), distribution.sample());
    }

    @Test
    void bothInputsAreRequired() {
        BinomialDistribution missingProbability = new BinomialDistribution();
        missingProbability.numTrialsInput.setValue(3, missingProbability);
        assertThrows(IllegalArgumentException.class, missingProbability::initAndValidate);

        BinomialDistribution missingTrialCount = new BinomialDistribution();
        missingTrialCount.pInput.setValue(probability(0.25), missingTrialCount);
        assertThrows(IllegalArgumentException.class, missingTrialCount::initAndValidate);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    void rejectsNegativeTrialCounts(int numTrials) {
        assertThrows(IllegalArgumentException.class, () -> distribution(numTrials, probability(0.25)));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void rejectsInvalidProbabilitiesAtInitializationAndAfterMutation(double probability) {
        assertThrows(IllegalArgumentException.class, () -> distribution(3, new MutableProbability(probability)));

        MutableProbability liveProbability = new MutableProbability(0.25);
        BinomialDistribution distribution = distribution(3, liveProbability);
        liveProbability.value = probability;
        assertNumericalMethodsRejectInvalidInputs(distribution);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void quantileRequiresAProbability(double probability) {
        BinomialDistribution distribution = distribution(3, probability(0.25));
        assertThrows(IllegalArgumentException.class, () -> distribution.inverseCumulativeProbability(probability));
    }

    @Test
    void seededSamplingIsReproducibleAndFollowsTheAnalyticCdf() {
        BinomialDistribution distribution = distribution(3, probability(0.25));
        List<Integer> expected = analyticSamples(61027, new double[] {27.0 / 64, 54.0 / 64, 63.0 / 64, 1.0});

        Randomizer.setSeed(61027);
        List<Integer> first = samples(distribution, expected.size());
        Randomizer.setSeed(61027);
        List<Integer> second = samples(distribution, expected.size());

        assertEquals(expected, first);
        assertEquals(first, second);
        assertTrue(first.stream().distinct().count() > 1);
    }

    @Test
    void everyNumericalMethodReadsTheCurrentProbabilityOnItsOwn() {
        // Each operation is the first access after changing p from 0.25 to 0.75.
        assertEquals(1.0 / 64, changedDistribution().density(0), TOLERANCE);
        assertEquals(Math.log(9.0 / 64), changedDistribution().logDensity(1), TOLERANCE);
        assertEquals(Math.log(9.0 / 64), changedDistribution().calculateLogP(), TOLERANCE);
        assertEquals(10.0 / 64, changedDistribution().cumulativeProbability(1), TOLERANCE);
        assertEquals(2, changedDistribution().inverseCumulativeProbability(0.5));
        assertEquals(2.25, changedDistribution().getMean(), TOLERANCE);
        assertEquals(3, changedDistribution().mostLikelyCount());

        List<Integer> expected = analyticSamples(61028, new double[] {1.0 / 64, 10.0 / 64, 37.0 / 64, 1.0});
        BinomialDistribution distribution = changedDistribution();
        Randomizer.setSeed(61028);
        assertEquals(expected, samples(distribution, expected.size()));
    }

    @Test
    void probabilityUpdatesIncludeEndpointsWithoutNarrowingTheCountBounds() {
        RealScalarParam<UnitInterval> probability = probability(0.25);
        BinomialDistribution distribution = distribution(3, probability);

        for (double value : new double[] {0.0, 1.0, 0.0, 0.25}) {
            probability.set(value);
            assertEquals(3 * value, distribution.getMean(), 0.0);
            assertBounds(distribution, 3);
            assertEquals(0, distribution.paramInput.get().getLower());
            assertEquals(3, distribution.paramInput.get().getUpper());
            if (value == 0.0 || value == 1.0) {
                int count = (int) (3 * value);
                assertEquals(count, distribution.inverseCumulativeProbability(0.0));
                assertEquals(count, distribution.inverseCumulativeProbability(1.0));
                assertEquals(List.of(count), distribution.sample());
            }
        }
    }

    @Test
    void probabilityChangesSmallerThanTheNativeCacheToleranceAreNotLost() {
        RealScalarParam<UnitInterval> probability = probability(0.25);
        BinomialDistribution distribution = distribution(3, probability);
        double changed = Math.nextUp(0.25);

        probability.set(changed);

        assertEquals(3 * changed, distribution.getMean(), 0.0);
        assertNotEquals(0.75, distribution.getMean());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    void trialCountCannotChangeAfterInitializationIncludingFromZero(int numTrials) {
        BinomialDistribution distribution = distribution(numTrials, probability(0.25));
        distribution.numTrialsInput.setValue(numTrials + 1, distribution);

        assertNumericalMethodsRejectInvalidInputs(distribution);
        assertThrows(IllegalArgumentException.class, distribution::getUpperBoundOfParameter);
    }

    private static BinomialDistribution distribution(int numTrials, RealScalar<? extends UnitInterval> probability) {
        BinomialDistribution distribution = new BinomialDistribution();
        distribution.numTrialsInput.setValue(numTrials, distribution);
        distribution.pInput.setValue(probability, distribution);
        IntScalarParam<NonNegativeInt> count =
                new IntScalarParam<>(Math.max(0, Math.min(1, numTrials)), NonNegativeInt.INSTANCE);
        // Native BoundUtils identifies the parameter by ID when applying distribution bounds.
        count.setID("count");
        distribution.paramInput.setValue(count, distribution);
        distribution.initAndValidate();
        return distribution;
    }

    private static RealScalarParam<UnitInterval> probability(double value) {
        return new RealScalarParam<>(value, UnitInterval.INSTANCE);
    }

    private static BinomialDistribution changedDistribution() {
        RealScalarParam<UnitInterval> probability = probability(0.25);
        BinomialDistribution distribution = distribution(3, probability);
        probability.set(0.75);
        return distribution;
    }

    private static void assertBounds(BinomialDistribution distribution, int numTrials) {
        assertEquals(0, distribution.getLowerBoundOfParameter());
        assertEquals(numTrials, distribution.getUpperBoundOfParameter());
        assertEquals(0.0, distribution.getLower());
        assertEquals((double) numTrials, distribution.getUpper());
        assertTrue(distribution.lowerInclusive());
        assertTrue(distribution.upperInclusive());
    }

    private static void assertNumericalMethodsRejectInvalidInputs(BinomialDistribution distribution) {
        assertThrows(IllegalArgumentException.class, () -> distribution.density(1));
        assertThrows(IllegalArgumentException.class, () -> distribution.logDensity(1));
        assertThrows(IllegalArgumentException.class, distribution::calculateLogP);
        assertThrows(IllegalArgumentException.class, () -> distribution.cumulativeProbability(1));
        assertThrows(IllegalArgumentException.class, () -> distribution.inverseCumulativeProbability(0.5));
        assertThrows(IllegalArgumentException.class, distribution::getMean);
        assertThrows(IllegalArgumentException.class, distribution::mostLikelyCount);
        assertThrows(IllegalArgumentException.class, distribution::sample);
    }

    private static List<Integer> analyticSamples(long seed, double[] cumulativeProbabilities) {
        Randomizer.setSeed(seed);
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            double draw = Randomizer.nextDouble();
            int count = 0;
            while (draw > cumulativeProbabilities[count]) {
                count++;
            }
            expected.add(count);
        }
        return expected;
    }

    private static List<Integer> samples(BinomialDistribution distribution, int count) {
        List<Integer> samples = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<Integer> sample = distribution.sample();
            assertEquals(1, sample.size());
            samples.add(sample.getFirst());
        }
        return samples;
    }

    /** Allows malformed values to reach the distribution's own input validation. */
    public static final class MutableProbability extends BEASTObject implements RealScalar<UnitInterval> {
        private double value;

        MutableProbability(double value) {
            this.value = value;
        }

        @Override
        public void initAndValidate() {}

        @Override
        public double get() {
            return value;
        }

        @Override
        public UnitInterval getDomain() {
            return UnitInterval.INSTANCE;
        }
    }
}
