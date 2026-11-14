package tiling;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.MarkovChainDistribution;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class LiveExponentialMarkovChainDistributionTest {
    private static final double TOLERANCE = 1e-12;

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 1.0, 4.0})
    void positiveChainsMatchNativeAndIndependentConditionalDensities(double initialMean) {
        for (double[] values :
                new double[][] {{0.125}, {1.0}, {3.5}, {0.25, 0.5, 0.75}, {1.0, 2.0, 4.0}, {4.0, 0.5, 2.0}}) {
            var mean = real(initialMean);
            var chain = vector(values);
            var distribution = distribution(mean, chain);
            assertSame(mean, distribution.initialMeanInput.get());
            assertSame(chain, distribution.paramInput.get());
            assertSame(Real.INSTANCE, mean.getDomain());
            assertSame(Real.INSTANCE, chain.getDomain());
            assertEquals(1.0, distribution.shapeInput.get());
            assertMatchesNativeAndFormula(distribution, mean, chain);
        }
    }

    private static Stream<Arguments> terminalZeroChains() {
        return Stream.of(
                Arguments.of((Object) new double[] {0.0}),
                Arguments.of((Object) new double[] {1.0, 0.0}),
                Arguments.of((Object) new double[] {0.5, 2.0, 0.0}));
    }

    @ParameterizedTest
    @MethodSource("terminalZeroChains")
    void finalZeroRetainsTheNativeShapeOneBoundaryDensity(double[] values) {
        var mean = real(2.0);
        var chain = vector(values);
        var distribution = distribution(mean, chain);
        assertMatchesNativeAndFormula(distribution, mean, chain);
        assertTrue(Double.isFinite(distribution.getCurrentLogP()));
        assertEquals(0.0, chain.get(chain.size() - 1));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.0, -1.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void invalidInitialMeanRejectsNonemptyChainsWithoutThrowing(double initialMean) {
        for (double[] values : new double[][] {{1.0}, {0.5, 1.0, 2.0}}) {
            var distribution = assertDoesNotThrow(() -> distribution(real(initialMean), vector(values)));
            assertEquals(Double.NEGATIVE_INFINITY, assertDoesNotThrow(distribution::calculateLogP));
            assertEquals(Double.NEGATIVE_INFINITY, distribution.getCurrentLogP());
        }
    }

    private static Stream<Arguments> invalidChains() {
        Stream<Arguments> invalidElements = Stream.of(
                        -0.5, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
                .flatMap(value -> Stream.of(
                        Arguments.of((Object) new double[] {value}),
                        Arguments.of((Object) new double[] {value, 1.0, 2.0}),
                        Arguments.of((Object) new double[] {0.5, value, 2.0}),
                        Arguments.of((Object) new double[] {0.5, 1.0, value})));
        return Stream.concat(
                invalidElements,
                Stream.of(
                        Arguments.of((Object) new double[] {0.0, 1.0, 2.0}),
                        Arguments.of((Object) new double[] {0.5, 0.0, 2.0}),
                        Arguments.of((Object) new double[] {0.0, 0.0})));
    }

    @ParameterizedTest
    @MethodSource("invalidChains")
    void invalidElementsAndInteriorZerosRejectWithoutThrowing(double[] values) {
        var chain = vector(values);
        var distribution = assertDoesNotThrow(() -> distribution(real(2.0), chain));
        assertEquals(Double.NEGATIVE_INFINITY, assertDoesNotThrow(distribution::calculateLogP));
        assertEquals(Double.NEGATIVE_INFINITY, distribution.getCurrentLogP());
        assertArrayEquals(values, chain.getValues(), 0.0, "Impossible observations must not be changed.");
    }

    @ParameterizedTest
    @ValueSource(doubles = {2.0, 0.0, -1.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void emptyChainHasUnitDensityEvenWithAnInvalidInitialMean(double initialMean) {
        var mean = real(initialMean);
        var empty = vector();
        var distribution = assertDoesNotThrow(() -> distribution(mean, empty));
        assertEquals(0, empty.size());
        assertEquals(0, empty.dimensionInput.get());
        assertEquals(0.0, assertDoesNotThrow(distribution::calculateLogP));
        assertEquals(0.0, distribution.getCurrentLogP());
        assertEquals(nativeDistribution(mean, empty).calculateLogP(), distribution.getCurrentLogP());
    }

    @Test
    void currentMeanAndEveryChainValueRemainLiveAcrossValidAndInvalidUpdates() {
        var mean = real(2.0);
        var chain = vector(0.5, 1.0, 2.0);
        var distribution = distribution(mean, chain);
        assertMatchesNativeAndFormula(distribution, mean, chain);
        double original = distribution.getCurrentLogP();

        mean.set(4.0);
        assertMatchesNativeAndFormula(distribution, mean, chain);
        assertNotEquals(original, distribution.getCurrentLogP());
        for (int i = 0; i < chain.size(); i++) {
            double previous = distribution.getCurrentLogP();
            chain.set(i, chain.get(i) + 0.25);
            assertMatchesNativeAndFormula(distribution, mean, chain);
            assertNotEquals(previous, distribution.getCurrentLogP());
        }

        mean.set(0.0);
        assertEquals(Double.NEGATIVE_INFINITY, assertDoesNotThrow(distribution::calculateLogP));
        mean.set(3.0);
        assertMatchesNativeAndFormula(distribution, mean, chain);
        chain.set(1, 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, assertDoesNotThrow(distribution::calculateLogP));
        chain.set(1, 1.5);
        assertMatchesNativeAndFormula(distribution, mean, chain);
        chain.set(2, 0.0);
        assertMatchesNativeAndFormula(distribution, mean, chain);
        chain.set(2, -1.0);
        assertEquals(Double.NEGATIVE_INFINITY, assertDoesNotThrow(distribution::calculateLogP));
        chain.set(2, 2.0);
        assertMatchesNativeAndFormula(distribution, mean, chain);
        assertSame(mean, distribution.initialMeanInput.get());
        assertSame(chain, distribution.paramInput.get());
    }

    @Test
    void inheritedLogDensityStoreAndRestoreSurvivesInvalidProposals() {
        var mean = real(2.0);
        var chain = vector(0.5, 1.0, 2.0);
        var distribution = distribution(mean, chain);
        double initial = distribution.calculateLogP();
        distribution.store();
        chain.set(1, 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, distribution.calculateLogP());
        distribution.restore();
        assertEquals(initial, distribution.getCurrentLogP(), 0.0);
        chain.set(1, 1.0);
        assertEquals(initial, distribution.calculateLogP(), TOLERANCE);

        mean.set(4.0);
        double updated = distribution.calculateLogP();
        assertNotEquals(initial, updated);
        distribution.store();
        mean.set(-1.0);
        assertEquals(Double.NEGATIVE_INFINITY, distribution.calculateLogP());
        distribution.restore();
        assertEquals(updated, distribution.getCurrentLogP(), 0.0);
        mean.set(4.0);
        assertEquals(updated, distribution.calculateLogP(), TOLERANCE);
    }

    private static void assertMatchesNativeAndFormula(
            LiveExponentialMarkovChainDistribution distribution,
            RealScalarParam<Real> mean,
            RealVectorParam<Real> chain) {
        double expected = 0.0;
        double previous = mean.get();
        for (double value : chain.getValues()) {
            expected += -Math.log(previous) - value / previous;
            previous = value;
        }
        double actual = distribution.calculateLogP();
        assertEquals(expected, actual, TOLERANCE);
        assertEquals(nativeDistribution(mean, chain).calculateLogP(), actual, TOLERANCE);
    }

    private static LiveExponentialMarkovChainDistribution distribution(
            RealScalarParam<Real> mean, RealVectorParam<Real> chain) {
        var distribution = new LiveExponentialMarkovChainDistribution();
        initialize(distribution, mean, chain);
        return distribution;
    }

    private static MarkovChainDistribution nativeDistribution(RealScalarParam<Real> mean, RealVectorParam<Real> chain) {
        var distribution = new MarkovChainDistribution();
        initialize(distribution, mean, chain);
        return distribution;
    }

    @SuppressWarnings("unchecked")
    private static void initialize(
            MarkovChainDistribution distribution, RealScalarParam<Real> mean, RealVectorParam<Real> chain) {
        // Native inputs only read these objects; keep the core Real domains and identities.
        RealScalar<PositiveReal> nativeMean = (RealScalar<PositiveReal>) (RealScalar<?>) mean;
        RealVector<PositiveReal> nativeChain = (RealVector<PositiveReal>) (RealVector<?>) chain;
        distribution.initByName("initialMean", nativeMean, "param", nativeChain, "shape", 1.0);
    }

    private static RealScalarParam<Real> real(double value) {
        return Double.isNaN(value) ? new NaNScalar() : new RealScalarParam<>(value, Real.INSTANCE);
    }

    private static RealVectorParam<Real> vector(double... values) {
        if (values.length == 0) {
            var empty = new RealVectorParam<Real>();
            empty.dimensionInput.setValue(0, empty);
            empty.initAndValidate();
            return empty;
        }
        return Arrays.stream(values).anyMatch(Double::isNaN)
                ? new NaNVector(values)
                : new RealVectorParam<>(values, Real.INSTANCE);
    }

    // Real parameters reject NaN at construction. These fixtures model malformed live
    // numeric inputs so the density's nonfinite guard is tested independently of that check.
    private static final class NaNScalar extends RealScalarParam<Real> {
        private NaNScalar() {
            super(0.0, Real.INSTANCE);
            value = Double.NaN;
        }
    }

    private static final class NaNVector extends RealVectorParam<Real> {
        private NaNVector(double[] values) {
            super(new double[values.length], Real.INSTANCE);
            this.values = values.clone();
        }
    }
}
