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

class GeometricDistributionTest {
    private static final double TOLERANCE = 1e-12;

    @Test
    void probabilitiesAndQuantilesCountFailuresStartingAtZero() {
        GeometricDistribution distribution = distribution(probability(0.25), count(0));
        assertTrue(distribution.isIntegerDistribution());
        assertBounds(distribution);
        assertTrue(distribution.getConditions().isEmpty());
        assertEquals(0, distribution.mostLikelyCount());
        assertEquals(3.0, distribution.getMean(), 0.0);

        double[] masses = {0.25, 0.1875, 0.140625, 0.10546875};
        double cdf = 0.0;
        for (int count = 0; count < masses.length; count++) {
            cdf += masses[count];
            assertEquals(masses[count], distribution.density(count), TOLERANCE);
            assertEquals(Math.log(masses[count]), distribution.logDensity(count), TOLERANCE);
            assertEquals(cdf, distribution.cumulativeProbability(count), TOLERANCE);
            assertEquals(cdf, distribution.cumulativeProbability(count + 0.9), TOLERANCE);
        }
        assertEquals(0.0, distribution.cumulativeProbability(-0.25), 0.0);
        assertEquals(0.0, distribution.cumulativeProbability(Double.NEGATIVE_INFINITY), 0.0);
        assertEquals(1.0, distribution.cumulativeProbability(Double.POSITIVE_INFINITY), 0.0);
        assertThrows(IllegalArgumentException.class, () -> distribution.cumulativeProbability(Double.NaN));
        assertEquals(0, distribution.inverseCumulativeProbability(0.0));
        assertEquals(0, distribution.inverseCumulativeProbability(0.25));
        assertEquals(1, distribution.inverseCumulativeProbability(0.3));
        assertEquals(2, distribution.inverseCumulativeProbability(0.5));
        assertEquals(8, distribution.inverseCumulativeProbability(0.9));
        assertEquals(16, distribution.inverseCumulativeProbability(0.99));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, -0.25, 0.5, 2.5, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void nonIntegerAndNegativeValuesHaveZeroMass(double value) {
        GeometricDistribution distribution = distribution(probability(0.25), count(0));
        assertEquals(Double.NEGATIVE_INFINITY, distribution.logDensity(value));
        assertEquals(0.0, distribution.density(value), 0.0);
    }

    @Test
    void probabilityOneIsAZeroPointMassWithoutNarrowingStateBounds() {
        IntScalarParam<NonNegativeInt> observation = count(3);
        GeometricDistribution distribution = distribution(probability(1.0), observation);
        assertSame(observation, distribution.paramInput.get());
        assertEquals(Double.NEGATIVE_INFINITY, distribution.calculateLogP());
        assertEquals(3, observation.get());
        assertEquals(0.0, distribution.logDensity(0), 0.0);
        assertEquals(1.0, distribution.density(0), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, distribution.logDensity(1));
        assertEquals(0.0, distribution.getMean(), 0.0);
        assertEquals(0, distribution.mostLikelyCount());
        assertEquals(1.0, distribution.cumulativeProbability(0), 0.0);
        assertEquals(0, distribution.inverseCumulativeProbability(0.0));
        assertEquals(0, distribution.inverseCumulativeProbability(0.5));
        assertEquals(0, distribution.inverseCumulativeProbability(1.0));
        assertEquals(List.of(0), distribution.sample());
        assertBounds(distribution);
    }

    @Test
    void boundValueAndSharedProbabilityRetainTheirIdentitiesAndConditionMetadata() {
        RealScalarParam<UnitInterval> p = probability(0.25);
        p.setID("successProbability");
        IntScalarParam<NonNegativeInt> count = count(2);
        count.setID("failures");
        GeometricDistribution first = distribution(p, count);
        GeometricDistribution second = distribution(p, count(0));

        assertSame(p, first.pInput.get());
        assertSame(p, second.pInput.get());
        assertSame(count, first.paramInput.get());
        assertEquals(List.of("successProbability"), first.getConditions());
        assertEquals(List.of("failures"), first.getArguments());
        for (double value : new double[] {0.25, 0.75, 1.0, 0.25}) {
            p.set(value);
            assertEquals(logMass(2, value), first.calculateLogP(), TOLERANCE);
            assertEquals(Math.log(value), second.calculateLogP(), TOLERANCE);
            assertBounds(first);
        }
        count.set(0);
        assertEquals(Math.log(0.25), first.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.1, 1.1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void rejectsInvalidInitializationAndScoresInvalidProposalsWithoutStaleProbabilities(double invalid) {
        MutableProbability p = new MutableProbability(invalid);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> distribution(p, count(0)));
        assertTrue(error.getMessage().contains("(0, 1]"), error.getMessage());

        p.value = 0.25;
        GeometricDistribution distribution = distribution(p, count(2));
        assertEquals(Math.log(0.140625), distribution.calculateLogP(), TOLERANCE);
        p.value = invalid;
        assertEquals(Double.NEGATIVE_INFINITY, distribution.calculateLogP());
        assertEquals(Double.NEGATIVE_INFINITY, distribution.logDensity(0));
        assertEquals(0.0, distribution.density(0), 0.0);
        assertThrows(IllegalArgumentException.class, distribution::sample);
        assertThrows(IllegalArgumentException.class, distribution::mostLikelyCount);
        assertThrows(IllegalArgumentException.class, distribution::getMean);
        assertThrows(IllegalArgumentException.class, () -> distribution.cumulativeProbability(0));
        assertThrows(IllegalArgumentException.class, () -> distribution.inverseCumulativeProbability(0.5));

        p.value = 0.75;
        assertEquals(Math.log(0.046875), distribution.calculateLogP(), TOLERANCE);
        p.value = 0.25;
        assertEquals(Math.log(0.140625), distribution.calculateLogP(), TOLERANCE);
        assertBounds(distribution);
    }

    @Test
    void probabilityIsRequiredAndAParameterIsOptionalForDistributionMethods() {
        GeometricDistribution missing = new GeometricDistribution();
        assertThrows(IllegalArgumentException.class, missing::initAndValidate);
        GeometricDistribution distribution = distribution(probability(0.25), null);
        assertEquals(0.25, distribution.density(0), 0.0);
        assertEquals(0, distribution.mostLikelyCount());
    }

    @Test
    void everyDistributionMethodReadsTheCurrentProbabilityWithoutADensityCall() {
        assertEquals(0.75, changedDistribution().density(0), 0.0);
        assertEquals(Math.log(0.1875), changedDistribution().logDensity(1), TOLERANCE);
        assertEquals(Math.log(0.1875), changedDistribution().calculateLogP(), TOLERANCE);
        assertEquals(0.9375, changedDistribution().cumulativeProbability(1), TOLERANCE);
        assertEquals(0, changedDistribution().inverseCumulativeProbability(0.5));
        assertEquals(1.0 / 3.0, changedDistribution().getMean(), TOLERANCE);
        assertEquals(0, changedDistribution().mostLikelyCount());

        GeometricDistribution changed = changedDistribution();
        Randomizer.setSeed(4637);
        List<Integer> expected = analyticSamples(0.75, 24);
        Randomizer.setSeed(4637);
        assertEquals(expected, samples(changed, expected.size()));
    }

    @Test
    void tinyProbabilityUpdatesAreNotLostToAnAbsoluteCacheTolerance() {
        RealScalarParam<UnitInterval> p = probability(1e-13);
        GeometricDistribution distribution = distribution(p, count(0));
        assertEquals(Math.log(1e-13), distribution.calculateLogP(), 0.0);
        p.set(2e-13);
        assertEquals(Math.log(2e-13), distribution.calculateLogP(), 0.0);
        p.set(1e-13);
        assertEquals(Math.log(1e-13), distribution.calculateLogP(), 0.0);
    }

    @Test
    void seededSamplingMatchesTheAnalyticCdfAndRefreshesAtTheEndpoint() {
        RealScalarParam<UnitInterval> p = probability(0.25);
        GeometricDistribution distribution = distribution(p, count(0));
        Randomizer.setSeed(4638);
        List<Integer> expected = analyticSamples(0.25, 24);
        Randomizer.setSeed(4638);
        assertEquals(expected, samples(distribution, expected.size()));
        Randomizer.setSeed(4638);
        assertEquals(expected, samples(distribution, expected.size()));
        assertTrue(expected.stream().distinct().count() > 1);
        p.set(1.0);
        assertEquals(List.of(0), distribution.sample());
        p.set(0.25);
        Randomizer.setSeed(4638);
        assertEquals(expected, samples(distribution, expected.size()));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void quantilesRequireAProbability(double probability) {
        GeometricDistribution distribution = distribution(probability(0.25), count(0));
        assertThrows(IllegalArgumentException.class, () -> distribution.inverseCumulativeProbability(probability));
    }

    @Test
    void unboundedTailIsNotClampedOrRenormalizedAtTheJavaIntegerLimit() {
        double p = 1e-12;
        GeometricDistribution distribution = distribution(probability(p), count(0));
        double max = Integer.MAX_VALUE;
        double cdfAtMax = -Math.expm1((max + 1.0) * Math.log1p(-p));
        assertEquals(cdfAtMax, distribution.cumulativeProbability(max), 0.0);
        assertTrue(cdfAtMax < 0.01);
        assertEquals(-Math.expm1((max + 2.0) * Math.log1p(-p)), distribution.cumulativeProbability(max + 1.0), 0.0);
        assertEquals(Math.log(p) + (max + 1.0) * Math.log1p(-p), distribution.logDensity(max + 1.0), 0.0);
        assertEquals(Math.log(p) + max * Math.log1p(-p), distribution.logDensity(max), 0.0);
        assertThrows(IllegalArgumentException.class, () -> distribution.inverseCumulativeProbability(0.5));
        assertThrows(IllegalArgumentException.class, () -> distribution.inverseCumulativeProbability(1.0));
        Randomizer.setSeed(4639);
        assertThrows(IllegalArgumentException.class, distribution::sample);

        GeometricDistribution ordinary = distribution(probability(0.25), count(0));
        // Its CDF at MAX rounds to 1, but the upper quantile is still infinite.
        assertEquals(1.0, ordinary.cumulativeProbability(max), 0.0);
        assertThrows(IllegalArgumentException.class, () -> ordinary.inverseCumulativeProbability(1.0));
    }

    private static GeometricDistribution changedDistribution() {
        RealScalarParam<UnitInterval> p = probability(0.25);
        GeometricDistribution distribution = distribution(p, count(1));
        p.set(0.75);
        return distribution;
    }

    private static RealScalarParam<UnitInterval> probability(double value) {
        return new RealScalarParam<>(value, UnitInterval.INSTANCE);
    }

    private static IntScalarParam<NonNegativeInt> count(int value) {
        return new IntScalarParam<>(value, NonNegativeInt.INSTANCE);
    }

    private static GeometricDistribution distribution(RealScalar<?> probability, IntScalarParam<NonNegativeInt> count) {
        GeometricDistribution distribution = new GeometricDistribution();
        distribution.pInput.setValue(probability, distribution);
        distribution.paramInput.setValue(count, distribution);
        distribution.initAndValidate();
        return distribution;
    }

    private static void assertBounds(GeometricDistribution distribution) {
        assertEquals(0, distribution.getLowerBoundOfParameter());
        assertEquals(Integer.MAX_VALUE, distribution.getUpperBoundOfParameter());
        assertEquals(0.0, distribution.getLower(), 0.0);
        assertEquals(Double.POSITIVE_INFINITY, distribution.getUpper());
        assertTrue(distribution.lowerInclusive());
        assertFalse(distribution.upperInclusive());
    }

    private static double logMass(int count, double probability) {
        if (count == 0) return Math.log(probability);
        return Math.log(probability) + count * Math.log1p(-probability);
    }

    private static List<Integer> samples(GeometricDistribution distribution, int number) {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < number; i++) values.add(distribution.sample().getFirst());
        return values;
    }

    private static List<Integer> analyticSamples(double probability, int number) {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            double uniform = Randomizer.nextDouble();
            double mass = probability;
            double cdf = mass;
            int count = 0;
            while (cdf < uniform) {
                mass *= 1.0 - probability;
                cdf += mass;
                count++;
            }
            values.add(count);
        }
        return values;
    }

    private static final class MutableProbability extends BEASTObject implements RealScalar<UnitInterval> {
        private double value;

        private MutableProbability(double value) {
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
