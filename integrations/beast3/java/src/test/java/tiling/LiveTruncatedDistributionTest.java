package tiling;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.distribution.OffsetReal;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.inference.distribution.TruncatedReal;
import beast.base.spec.inference.distribution.Uniform;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beast.base.util.Randomizer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LiveTruncatedDistributionTest {
    private static final double TOLERANCE = 1e-12;

    @Test
    void halfNormalHasTwiceTheOriginalDensityAndProperCdf() {
        var base = normal(real(0.0), positive(1.0));
        var distribution = truncated(base, 0.0, null);
        assertEquals(normalLogDensity(1.0, 0.0, 1.0) + Math.log(2.0), distribution.logDensity(1.0), TOLERANCE);
        assertEquals(Math.sqrt(2.0 / Math.PI), distribution.density(0.0), TOLERANCE);
        assertEquals(Double.NEGATIVE_INFINITY, distribution.logDensity(-0.1));
        assertEquals(0.0, distribution.cumulativeProbability(0.0), 0.0);
        assertEquals(0.6826894921370859, distribution.cumulativeProbability(1.0), TOLERANCE);
        assertEquals(0.6744897501960817, distribution.inverseCumulativeProbability(0.5), TOLERANCE);
        assertEquals(0.0, distribution.inverseCumulativeProbability(0.0), 0.0);
        assertEquals(Double.POSITIVE_INFINITY, distribution.inverseCumulativeProbability(1.0));
        assertNull(base.paramInput.get());
        assertFalse(base.getOutputs().contains(null));
    }

    @Test
    void omittedLowerAndBothOmittedBoundsRetainTheirMeaning() {
        var lowerHalf = truncated(normal(real(0.0), positive(1.0)), null, 0.0);
        assertEquals(normalLogDensity(-1.0, 0.0, 1.0) + Math.log(2.0), lowerHalf.logDensity(-1.0), TOLERANCE);
        assertEquals(0.31731050786291415, lowerHalf.cumulativeProbability(-1.0), TOLERANCE);
        assertEquals(-0.6744897501960817, lowerHalf.inverseCumulativeProbability(0.5), TOLERANCE);
        assertEquals(Double.NEGATIVE_INFINITY, lowerHalf.inverseCumulativeProbability(0.0));
        assertEquals(0.0, lowerHalf.inverseCumulativeProbability(1.0), 0.0);

        var unrestricted = truncated(normal(real(1.0), positive(2.0)), null, null);
        assertEquals(normalLogDensity(-0.5, 1.0, 2.0), unrestricted.logDensity(-0.5), TOLERANCE);
        assertEquals(0.5, unrestricted.cumulativeProbability(1.0), TOLERANCE);
        assertEquals(1.0, unrestricted.inverseCumulativeProbability(0.5), TOLERANCE);
    }

    @Test
    void boundedNormalUsesTheConditionalDensity() {
        var distribution = truncated(normal(real(0.0), positive(1.0)), -1.0, 1.0);
        double mass = 0.6826894921370859;
        assertEquals(normalLogDensity(0.0, 0.0, 1.0) - Math.log(mass), distribution.logDensity(0.0), TOLERANCE);
        assertEquals(0.5, distribution.cumulativeProbability(0.0), TOLERANCE);
        assertEquals(0.0, distribution.inverseCumulativeProbability(0.5), TOLERANCE);
        assertEquals(-1.0, distribution.getLower(), 0.0);
        assertEquals(1.0, distribution.getUpper(), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, distribution.logDensity(1.1));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, 1.0})
    void normalFarTailsRetainFiniteNormalizationQuantilesAndSamples(double sign) {
        double lower = sign > 0 ? 8.0 : -9.0;
        double upper = sign > 0 ? 9.0 : -8.0;
        var distribution = truncated(normal(real(0.0), positive(1.0)), lower, upper);
        // Phi(-8) - Phi(-9), also equal to the normal mass between 8 and 9.
        double mass = 6.21983198586583e-16;
        assertEquals(
                normalLogDensity(sign * 8.5, 0.0, 1.0) - Math.log(mass), distribution.logDensity(sign * 8.5), 2e-12);
        assertEquals(lower, distribution.inverseCumulativeProbability(0.0), 0.0);
        assertEquals(upper, distribution.inverseCumulativeProbability(1.0), 0.0);
        for (double p : new double[] {0.01, 0.25, 0.5, 0.75, 0.99}) {
            double quantile = distribution.inverseCumulativeProbability(p);
            assertTrue(quantile > lower && quantile < upper);
            assertEquals(p, distribution.cumulativeProbability(quantile), 1e-12);
        }
        Randomizer.setSeed(7418);
        for (int i = 0; i < 100; i++) {
            double sample = distribution.sample().getFirst();
            assertTrue(sample > lower && sample < upper);
            assertTrue(Double.isFinite(distribution.logDensity(sample)));
        }
    }

    @Test
    void normalLogDensityDoesNotUnderflowThroughItsDensity() {
        var distribution = truncated(normal(real(0.0), positive(1.0)), null, null);
        assertEquals(normalLogDensity(40.0, 0.0, 1.0), distribution.logDensity(40.0), TOLERANCE);
        assertTrue(Double.isFinite(distribution.logDensity(40.0)));
    }

    @Test
    void boundsAndQuantileEndpointsIntersectTheBaseSupport() {
        var distribution = truncated(uniform(real(1.0), real(5.0)), 0.0, 3.0);
        assertEquals(1.0, distribution.getLowerBoundOfParameter(), 0.0);
        assertEquals(3.0, distribution.getUpperBoundOfParameter(), 0.0);
        assertEquals(1.0, distribution.inverseCumulativeProbability(0.0), 0.0);
        assertEquals(3.0, distribution.inverseCumulativeProbability(1.0), 0.0);
        assertEquals(0.5, distribution.density(2.0), TOLERANCE);
        assertEquals(0.5, distribution.cumulativeProbability(2.0), TOLERANCE);
        assertEquals(Double.NEGATIVE_INFINITY, distribution.logDensity(0.5));
    }

    @Test
    void impossibleObservedValueIsRetainedWithZeroDensity() {
        var observation = real(-1.0);
        observation.setID("observed");
        var distribution = new LiveTruncatedDistribution();
        assertDoesNotThrow(() -> distribution.initByName(
                "distribution", normal(real(0.0), positive(1.0)), "lower", real(0.0), "param", observation));
        assertSame(observation, distribution.paramInput.get());
        assertEquals(-1.0, observation.get(), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, distribution.calculateLogP());
    }

    @Test
    void liveBaseChangesUpdateBothDensityAndNormalizingMass() {
        var mean = real(0.0);
        var sd = positive(1.0);
        var base = normal(mean, sd);
        var distribution = truncated(base, 0.0, 2.0);
        assertSame(mean, base.meanInput.get());
        assertSame(sd, base.sdInput.get());
        double previous = distribution.logDensity(0.5);
        mean.set(1.0);
        assertNotEquals(previous, distribution.logDensity(0.5));
        assertEquals(0.5, distribution.cumulativeProbability(1.0), TOLERANCE);
        assertEquals(
                normalLogDensity(0.5, 1.0, 1.0) - Math.log(0.6826894921370859),
                distribution.logDensity(0.5),
                TOLERANCE);
        sd.set(2.0);
        assertEquals(
                normalLogDensity(0.5, 1.0, 2.0) - Math.log(0.38292492254802624),
                distribution.logDensity(0.5),
                TOLERANCE);
        assertEquals(1.0, distribution.inverseCumulativeProbability(0.5), TOLERANCE);
    }

    @Test
    void supportDisjointProposalHasZeroDensityAndCanRecover() {
        var lower = real(0.0);
        var upper = real(3.0);
        var distribution = truncated(uniform(lower, upper), 1.0, 2.0);
        assertEquals(0.0, distribution.logDensity(1.5), TOLERANCE);
        upper.set(0.5);
        assertEquals(Double.NEGATIVE_INFINITY, distribution.logDensity(1.5));
        assertThrows(IllegalArgumentException.class, distribution::validateInterval);
        upper.set(3.0);
        assertEquals(0.0, distribution.logDensity(1.5), TOLERANCE);
    }

    @ParameterizedTest
    @CsvSource({"1.0,1.0", "2.0,1.0"})
    void rejectsZeroWidthOrReversedIntervals(double lower, double upper) {
        RuntimeException failure =
                assertThrows(RuntimeException.class, () -> truncated(normal(real(0.0), positive(1.0)), lower, upper));
        assertTrue(failure.getMessage().contains("lower < upper"));
    }

    @Test
    void rejectsInitiallyDisjointSupport() {
        RuntimeException failure =
                assertThrows(RuntimeException.class, () -> truncated(uniform(real(0.0), real(1.0)), 2.0, 3.0));
        assertTrue(failure.getMessage().contains("no positive probability mass"));
    }

    @Test
    void numericalZeroIsReportedRatherThanTreatedAsAnImpossibleProposal() {
        var mean = real(0.0);
        var distribution = truncated(normal(mean, positive(1.0)), 0.0, 1.0);
        mean.set(50.0);
        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> distribution.logDensity(0.5));
        assertTrue(failure.getMessage().contains("cannot be represented"));
        assertThrows(IllegalArgumentException.class, distribution::sample);
        mean.set(0.0);
        assertTrue(Double.isFinite(distribution.logDensity(0.5)));
    }

    @Test
    void rejectsComposedBasesInsteadOfUsingTheirUntransformedApacheLaw() {
        var base = normal(real(0.0), positive(1.0));
        var offset = new OffsetReal(base, 2.0);
        var truncated = new TruncatedReal(base, -1.0, 1.0);
        for (var wrapper : List.of(offset, truncated)) {
            RuntimeException failure = assertThrows(RuntimeException.class, () -> truncated(wrapper, -0.5, 0.5));
            assertTrue(failure.getMessage().contains("directly backed"));
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
    void rejectsInvalidQuantileProbabilities(double probability) {
        var distribution = truncated(normal(real(0.0), positive(1.0)), -1.0, 1.0);
        assertThrows(IllegalArgumentException.class, () -> distribution.inverseCumulativeProbability(probability));
    }

    @Test
    void directSamplingIsSeededAndUsesCurrentParameters() {
        var mean = real(0.0);
        var distribution = truncated(normal(mean, positive(1.0)), -1.0, 2.0);
        Randomizer.setSeed(62108);
        List<Double> first = samples(distribution, 32);
        Randomizer.setSeed(62108);
        assertEquals(first, samples(distribution, 32));
        mean.set(1.0);
        Randomizer.setSeed(62108);
        List<Double> updated = samples(distribution, 32);
        assertNotEquals(first, updated);
        for (double value : updated) assertTrue(value >= -1.0 && value <= 2.0);
    }

    private static List<Double> samples(LiveTruncatedDistribution distribution, int count) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(distribution.sample().getFirst());
        return result;
    }

    private static double normalLogDensity(double value, double mean, double sd) {
        double residual = (value - mean) / sd;
        return -0.5 * Math.log(2.0 * Math.PI) - Math.log(sd) - 0.5 * residual * residual;
    }

    private static LiveTruncatedDistribution truncated(
            ScalarDistribution<RealScalar<Real>, Double> base, Double lower, Double upper) {
        var distribution = new LiveTruncatedDistribution();
        List<Object> arguments = new ArrayList<>(List.of("distribution", base));
        if (lower != null) arguments.addAll(List.of("lower", real(lower)));
        if (upper != null) arguments.addAll(List.of("upper", real(upper)));
        distribution.initByName(arguments.toArray());
        return distribution;
    }

    private static Normal normal(RealScalarParam<Real> mean, RealScalarParam<PositiveReal> sd) {
        var distribution = new Normal();
        distribution.initByName("mean", mean, "sigma", sd);
        return distribution;
    }

    private static Uniform uniform(RealScalarParam<Real> lower, RealScalarParam<Real> upper) {
        var distribution = new Uniform();
        distribution.initByName("lower", lower, "upper", upper);
        return distribution;
    }

    private static RealScalarParam<Real> real(double value) {
        return new RealScalarParam<>(value, Real.INSTANCE);
    }

    private static RealScalarParam<PositiveReal> positive(double value) {
        return new RealScalarParam<>(value, PositiveReal.INSTANCE);
    }
}
