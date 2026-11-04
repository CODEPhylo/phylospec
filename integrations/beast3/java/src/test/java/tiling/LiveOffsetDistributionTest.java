package tiling;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.inference.distribution.Uniform;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beast.base.util.Randomizer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LiveOffsetDistributionTest {
    private static final double TOLERANCE = 1e-12;

    @ParameterizedTest
    @ValueSource(doubles = {-2.0, 0.0, 3.0})
    void shiftedNormalMatchesItsAnalyticDistribution(double offset) {
        LiveOffsetDistribution shifted = shifted(normal(real(1.0), positive(2.0)), real(offset));
        var expected = org.apache.commons.statistics.distribution.NormalDistribution.of(1.0 + offset, 2.0);

        for (double value : new double[] {-3.0, 0.0, 2.0, 7.0}) {
            assertEquals(expected.density(value), shifted.density(value), TOLERANCE);
            assertEquals(expected.logDensity(value), shifted.logDensity(value), TOLERANCE);
            assertEquals(expected.cumulativeProbability(value), shifted.cumulativeProbability(value), TOLERANCE);
        }
        for (double p : new double[] {0.0, 0.01, 0.5, 0.99, 1.0}) {
            assertEquals(expected.inverseCumulativeProbability(p), shifted.inverseCumulativeProbability(p), TOLERANCE);
        }
        assertEquals(1.0 + offset, shifted.getMean(), TOLERANCE);
        assertEquals(Double.NEGATIVE_INFINITY, shifted.getLower());
        assertEquals(Double.POSITIVE_INFINITY, shifted.getUpper());
        assertFalse(shifted.isIntegerDistribution());
        assertTrue(shifted.getConditions().isEmpty());
    }

    @Test
    void shiftedUniformRetainsItsSupportAndCdf() {
        LiveOffsetDistribution shifted = shifted(uniform(real(1.0), real(5.0)), real(-3.0));

        assertEquals(-2.0, shifted.getLowerBoundOfParameter(), 0.0);
        assertEquals(2.0, shifted.getUpperBoundOfParameter(), 0.0);
        assertEquals(-2.0, shifted.getLower(), 0.0);
        assertEquals(2.0, shifted.getUpper(), 0.0);
        assertTrue(shifted.lowerInclusive());
        assertTrue(shifted.upperInclusive());
        assertEquals(0.25, shifted.density(-2.0), TOLERANCE);
        assertEquals(0.25, shifted.density(2.0), TOLERANCE);
        assertEquals(Double.NEGATIVE_INFINITY, shifted.logDensity(-3.0));
        assertEquals(Double.NEGATIVE_INFINITY, shifted.logDensity(3.0));
        assertEquals(0.0, shifted.cumulativeProbability(-3.0), 0.0);
        assertEquals(0.0, shifted.cumulativeProbability(-2.0), 0.0);
        assertEquals(0.25, shifted.cumulativeProbability(-1.0), TOLERANCE);
        assertEquals(0.5, shifted.cumulativeProbability(0.0), TOLERANCE);
        assertEquals(1.0, shifted.cumulativeProbability(2.0), 0.0);
        assertEquals(1.0, shifted.cumulativeProbability(3.0), 0.0);
        assertEquals(-2.0, shifted.inverseCumulativeProbability(0.0), 0.0);
        assertEquals(0.0, shifted.inverseCumulativeProbability(0.5), TOLERANCE);
        assertEquals(2.0, shifted.inverseCumulativeProbability(1.0), 0.0);
    }

    @Test
    void impossibleObservationIsRetainedAndHasZeroDensity() {
        var base = uniform(real(1.0), real(5.0));
        var observation = real(4.0);
        observation.setID("observed");
        LiveOffsetDistribution shifted = new LiveOffsetDistribution();
        assertDoesNotThrow(() -> shifted.initByName("distribution", base, "offset", real(-3.0), "param", observation));

        assertSame(observation, shifted.paramInput.get());
        assertNull(base.paramInput.get());
        assertEquals(List.of("observed"), shifted.getArguments());
        assertEquals(Double.NEGATIVE_INFINITY, shifted.calculateLogP());
    }

    @Test
    void liveOffsetAndBaseParametersRetainIdentityAndUpdateTheDensity() {
        var mean = real(1.0);
        var sd = positive(2.0);
        var offset = real(3.0);
        offset.setID("offset");
        var base = normal(mean, sd);
        base.setID("base");
        var observation = real(0.5);
        LiveOffsetDistribution shifted = new LiveOffsetDistribution();
        shifted.initByName("distribution", base, "offset", offset, "param", observation);

        assertSame(base, shifted.distributionInput.get());
        assertSame(offset, shifted.offsetInput.get());
        assertSame(mean, base.meanInput.get());
        assertSame(sd, base.sdInput.get());
        assertEquals(List.of("base", "offset"), shifted.getConditions());
        assertEquals(normalLogDensity(0.5, 4.0, 2.0), shifted.calculateLogP(), TOLERANCE);
        mean.set(-2.0);
        sd.set(0.5);
        offset.set(1.0);
        assertEquals(normalLogDensity(0.5, -1.0, 0.5), shifted.calculateLogP(), TOLERANCE);
        assertEquals(-1.0, shifted.getMean(), TOLERANCE);
    }

    @Test
    void eachNumericalOperationReadsTheCurrentBaseAndOffset() {
        var lower = real(1.0);
        var upper = real(5.0);
        var offset = real(3.0);
        var shifted = shifted(uniform(lower, upper), offset);

        lower.set(2.0);
        upper.set(8.0);
        offset.set(-4.0);
        assertEquals(0.5, shifted.cumulativeProbability(1.0), TOLERANCE);
        assertEquals(1.0 / 6.0, shifted.density(0.0), TOLERANCE);
        assertEquals(-2.0, shifted.getLower(), 0.0);
        assertEquals(4.0, shifted.getUpper(), 0.0);
        assertEquals(1.0, shifted.inverseCumulativeProbability(0.5), TOLERANCE);
        assertEquals(1.0, shifted.getMean(), TOLERANCE);
    }

    @Test
    void seededSamplingUsesCurrentParametersWithoutMutatingBaseSamples() {
        var mean = real(1.0);
        var sd = positive(2.0);
        var offset = real(3.0);
        var base = normal(mean, sd);
        // Prime the base sampler before updating its parameters later in this test.
        base.sample();
        var shifted = shifted(base, offset);
        Randomizer.setSeed(61029);
        List<Double> first = samples(shifted, 32);
        Randomizer.setSeed(61029);
        assertEquals(first, samples(shifted, 32));

        mean.set(-4.0);
        sd.set(0.5);
        offset.set(-2.0);
        var expected = org.apache.commons.statistics.distribution.NormalDistribution.of(-6.0, 0.5);
        Randomizer.setSeed(61030);
        List<Double> analytic = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            analytic.add(expected.inverseCumulativeProbability(Randomizer.nextDouble()));
        }
        Randomizer.setSeed(61030);
        List<Double> actual = samples(shifted, 32);
        for (int i = 0; i < actual.size(); i++) {
            assertEquals(analytic.get(i), actual.get(i), TOLERANCE);
            assertTrue(Double.isFinite(actual.get(i)));
        }
    }

    @Test
    void nestedOffsetsComposeTheirDensityCdfAndSupport() {
        var inner = shifted(uniform(real(1.0), real(5.0)), real(-3.0));
        var outer = shifted(inner, real(4.0));

        assertEquals(2.0, outer.getLower(), 0.0);
        assertEquals(6.0, outer.getUpper(), 0.0);
        assertEquals(Math.log(0.25), outer.logDensity(3.0), TOLERANCE);
        assertEquals(0.25, outer.cumulativeProbability(3.0), TOLERANCE);
        assertEquals(4.0, outer.inverseCumulativeProbability(0.5), TOLERANCE);
    }

    private static List<Double> samples(LiveOffsetDistribution distribution, int count) {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < count; i++) values.add(distribution.sample().getFirst());
        return values;
    }

    private static double normalLogDensity(double value, double mean, double sd) {
        double z = (value - mean) / sd;
        return -0.5 * Math.log(2.0 * Math.PI) - Math.log(sd) - z * z / 2.0;
    }

    private static LiveOffsetDistribution shifted(
            ScalarDistribution<RealScalar<Real>, Double> base, RealScalarParam<Real> offset) {
        LiveOffsetDistribution shifted = new LiveOffsetDistribution();
        shifted.initByName("distribution", base, "offset", offset);
        return shifted;
    }

    private static Normal normal(RealScalarParam<Real> mean, RealScalarParam<PositiveReal> sd) {
        Normal normal = new Normal();
        normal.initByName("mean", mean, "sigma", sd);
        return normal;
    }

    private static Uniform uniform(RealScalarParam<Real> lower, RealScalarParam<Real> upper) {
        Uniform uniform = new Uniform();
        uniform.initByName("lower", lower, "upper", upper);
        return uniform;
    }

    private static RealScalarParam<Real> real(double value) {
        return new RealScalarParam<>(value, Real.INSTANCE);
    }

    private static RealScalarParam<PositiveReal> positive(double value) {
        return new RealScalarParam<>(value, PositiveReal.INSTANCE);
    }
}
