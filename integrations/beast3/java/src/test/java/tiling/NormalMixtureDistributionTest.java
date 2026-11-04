package tiling;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.core.BEASTObject;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.type.Simplex;
import beast.base.util.Randomizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class NormalMixtureDistributionTest {
    private static final double TOLERANCE = 1e-12;

    @ParameterizedTest
    @ValueSource(doubles = {-3.0, 0.0, 0.5, 4.0})
    void densityIsAWeightedSumNotAProduct(double value) {
        var first = normal(real(-1.0), positive(1.0));
        var second = normal(real(2.0), positive(0.5));
        var mixture = mixture(List.of(first, second), simplex(0.25, 0.75), real(value));
        double expected =
                Math.log(0.25 * Math.exp(logNormal(value, -1.0, 1.0)) + 0.75 * Math.exp(logNormal(value, 2.0, 0.5)));
        assertEquals(expected, mixture.calculateLogP(), TOLERANCE);
        assertEquals(logNormal(value, -1.0, 1.0), first.getCurrentLogP(), TOLERANCE);
        assertEquals(logNormal(value, 2.0, 0.5), second.getCurrentLogP(), TOLERANCE);
        assertSame(first.paramInput.get(), second.paramInput.get());
        assertNotEquals(first.getCurrentLogP() + second.getCurrentLogP(), mixture.getCurrentLogP());
    }

    @Test
    void sharedParameterBindingRefreshesAnAlreadyInitializedNativeNormal() {
        var first = normal(real(0.0), positive(1.0));
        var second = normal(real(0.0), positive(1.0));
        assertNull(first.paramInput.get());
        var value = real(0.5);
        var mixture = mixture(List.of(first, second), simplex(0.5, 0.5), value);
        assertEquals(logNormal(0.5, 0.0, 1.0), mixture.calculateLogP(), TOLERANCE);
        value.set(-2.0);
        assertEquals(logNormal(-2.0, 0.0, 1.0), mixture.calculateLogP(), TOLERANCE);
    }

    @Test
    void tailLogDensityDoesNotUnderflowThroughLinearDensities() {
        var mixture = mixture(
                List.of(normal(real(0.0), positive(1.0)), normal(real(1.0), positive(1.0))),
                simplex(0.25, 0.75),
                real(40.0));
        assertEquals(0.0, Math.exp(logNormal(40.0, 0.0, 1.0)));
        assertEquals(0.0, Math.exp(logNormal(40.0, 1.0, 1.0)));
        double second = Math.log(0.75) + logNormal(40.0, 1.0, 1.0);
        double expected = second + Math.log1p(Math.exp(Math.log(0.25) + logNormal(40.0, 0.0, 1.0) - second));
        assertTrue(Double.isFinite(mixture.calculateLogP()));
        assertEquals(expected, mixture.getCurrentLogP(), TOLERANCE);
    }

    @Test
    void currentWeightsMeanAndScaleEachChangeTheDensity() {
        var mean = real(-1.0);
        var scale = positive(1.0);
        var weights = simplex(0.25, 0.75);
        var mixture = mixture(List.of(normal(mean, scale), normal(real(2.0), positive(1.0))), weights, real(0.0));
        double old = mixture.calculateLogP();
        weights.set(0, 0.75);
        weights.set(1, 0.25);
        double changedWeights = mixture.calculateLogP();
        assertNotEquals(old, changedWeights);
        assertEquals(expected(0.0, -1.0, 1.0, 2.0, 1.0, 0.75), changedWeights, TOLERANCE);
        mean.set(0.5);
        double changedMean = mixture.calculateLogP();
        assertNotEquals(changedWeights, changedMean);
        assertEquals(expected(0.0, 0.5, 1.0, 2.0, 1.0, 0.75), changedMean, TOLERANCE);
        scale.set(2.0);
        double changedScale = mixture.calculateLogP();
        assertNotEquals(changedMean, changedScale);
        assertEquals(expected(0.0, 0.5, 2.0, 2.0, 1.0, 0.75), changedScale, TOLERANCE);
    }

    @Test
    void equalComponentDensitiesAtTheSymmetricMidpointDoNotDependOnWeights() {
        var weights = simplex(0.25, 0.75);
        var mixture = mixture(
                List.of(normal(real(-1.0), positive(1.0)), normal(real(2.0), positive(1.0))), weights, real(0.5));
        double expected = logNormal(0.5, -1.0, 1.0);
        assertEquals(expected, mixture.calculateLogP(), TOLERANCE);
        weights.set(0, 0.75);
        weights.set(1, 0.25);
        assertEquals(expected, mixture.calculateLogP(), TOLERANCE);
    }

    @Test
    void oneComponentRetainsItsNativeDensity() {
        var base = normal(real(2.0), positive(0.5));
        var mixture = mixture(List.of(base), simplex(1.0), real(1.25));
        assertEquals(logNormal(1.25, 2.0, 0.5), mixture.calculateLogP(), TOLERANCE);
        assertEquals(base.getCurrentLogP(), mixture.getCurrentLogP());
    }

    @Test
    void zeroWeightsDropComponentsWithoutCreatingNaN() {
        var mixture = mixture(
                List.of(
                        normal(real(-10.0), positive(1.0)),
                        normal(real(2.0), positive(0.5)),
                        normal(real(20.0), positive(2.0))),
                simplex(0.0, 1.0, 0.0),
                real(1.25));
        assertEquals(logNormal(1.25, 2.0, 0.5), mixture.calculateLogP(), TOLERANCE);
        assertTrue(Double.isNaN(mixture.componentsInput.get().getFirst().getCurrentLogP()));
    }

    @Test
    void moreThanTwoActiveComponentsContributeTheirWeights() {
        var mixture = mixture(
                List.of(
                        normal(real(-2.0), positive(1.0)),
                        normal(real(0.0), positive(2.0)),
                        normal(real(3.0), positive(0.5))),
                simplex(0.2, 0.3, 0.5),
                real(0.5));
        double expected = Math.log(0.2 * Math.exp(logNormal(0.5, -2.0, 1.0))
                + 0.3 * Math.exp(logNormal(0.5, 0.0, 2.0))
                + 0.5 * Math.exp(logNormal(0.5, 3.0, 0.5)));
        assertEquals(expected, mixture.calculateLogP(), TOLERANCE);
    }

    @Test
    void directConditionalSamplingIsSeededAndReadsUpdatedInputs() {
        var mean = real(-3.0);
        var sd = positive(0.5);
        var weights = simplex(1.0, 0.0);
        var observation = real(0.25);
        var mixture = mixture(List.of(normal(mean, sd), normal(real(4.0), positive(1.0))), weights, observation);
        Randomizer.setSeed(82031);
        List<Double> original = samples(mixture, 32);
        Randomizer.setSeed(82031);
        assertEquals(original, samples(mixture, 32));
        mean.set(-1.0);
        Randomizer.setSeed(82031);
        List<Double> shifted = samples(mixture, 32);
        for (int i = 0; i < original.size(); i++) assertEquals(original.get(i) + 2.0, shifted.get(i), TOLERANCE);
        sd.set(1.0);
        Randomizer.setSeed(82031);
        List<Double> scaled = samples(mixture, 32);
        for (int i = 0; i < original.size(); i++)
            assertEquals(-1.0 + 2.0 * (shifted.get(i) + 1.0), scaled.get(i), TOLERANCE);
        weights.set(0, 0.0);
        weights.set(1, 1.0);
        Randomizer.setSeed(82031);
        List<Double> other = samples(mixture, 32);
        for (int i = 0; i < original.size(); i++) assertEquals(scaled.get(i) + 5.0, other.get(i), TOLERANCE);
        assertEquals(0.25, observation.get(), 0.0, "Conditional sampling must not overwrite observed data.");
    }

    @Test
    void directSamplingUsesAllPositiveWeights() {
        var mixture = mixture(
                List.of(
                        normal(real(-10.0), positive(0.1)),
                        normal(real(0.0), positive(0.1)),
                        normal(real(10.0), positive(0.1))),
                simplex(0.2, 0.3, 0.5),
                real(0.0));
        Randomizer.setSeed(76308);
        int[] counts = new int[3];
        for (double value : samples(mixture, 2000)) {
            counts[value < -5.0 ? 0 : value < 5.0 ? 1 : 2]++;
        }
        assertEquals(0.2, counts[0] / 2000.0, 0.05);
        assertEquals(0.3, counts[1] / 2000.0, 0.05);
        assertEquals(0.5, counts[2] / 2000.0, 0.05);
    }

    @Test
    void logProbabilityUsesInheritedStoreAndRestore() {
        var weights = simplex(0.25, 0.75);
        var mixture = mixture(
                List.of(normal(real(-1.0), positive(1.0)), normal(real(2.0), positive(0.5))), weights, real(0.5));
        double accepted = mixture.calculateLogP();
        mixture.store();
        weights.set(0, 0.75);
        weights.set(1, 0.25);
        assertNotEquals(accepted, mixture.calculateLogP());
        mixture.restore();
        assertEquals(accepted, mixture.getCurrentLogP(), 0.0);
        weights.set(0, 0.25);
        weights.set(1, 0.75);
        assertEquals(accepted, mixture.calculateLogP(), TOLERANCE);
    }

    @Test
    void metadataNamesTheSingleValueAndUniqueConditions() {
        var mean = real(0.0);
        var sd = positive(1.0);
        var weights = simplex(0.25, 0.75);
        var value = real(0.5);
        mean.setID("mean");
        sd.setID("sd");
        weights.setID("weights");
        value.setID("value");
        var mixture = mixture(List.of(normal(mean, sd), normal(mean, sd)), weights, value);
        assertEquals(List.of("value"), mixture.getArguments());
        assertEquals(List.of("weights", "mean", "sd"), mixture.getConditions());
    }

    private static Stream<Arguments> invalidWeights() {
        return Stream.of(
                Arguments.of((Object) new double[] {}),
                Arguments.of((Object) new double[] {1.0}),
                Arguments.of((Object) new double[] {0.0, 0.0}),
                Arguments.of((Object) new double[] {-0.1, 1.1}),
                Arguments.of((Object) new double[] {Double.NaN, 1.0}),
                Arguments.of((Object) new double[] {Double.POSITIVE_INFINITY, 0.0}),
                Arguments.of((Object) new double[] {0.2, 0.3}));
    }

    @ParameterizedTest
    @MethodSource("invalidWeights")
    void malformedWeightsAreRejected(double[] weights) {
        assertThrows(
                IllegalArgumentException.class,
                () -> mixture(
                        List.of(normal(real(0.0), positive(1.0)), normal(real(1.0), positive(1.0))),
                        new MutableSimplex(weights),
                        real(0.0)));
    }

    @Test
    void componentCountCannotChangeAfterValidation() {
        var weights = new MutableSimplex(new double[] {0.5, 0.5});
        var value = real(0.0);
        var mixture =
                mixture(List.of(normal(real(0.0), positive(1.0)), normal(real(1.0), positive(1.0))), weights, value);
        var extra = normal(real(2.0), positive(1.0));
        extra.paramInput.setValue(value, extra);
        mixture.componentsInput.get().add(extra);
        weights.values = new double[] {0.2, 0.3, 0.5};
        assertThrows(IllegalArgumentException.class, mixture::calculateLogP);
        assertThrows(IllegalArgumentException.class, mixture::sample);
    }

    @Test
    void unboundOrDifferentlyBoundComponentsAreRejected() {
        var mixture = new NormalMixtureDistribution();
        mixture.weightsInput.setValue(simplex(1.0), mixture);
        var base = normal(real(0.0), positive(1.0));
        mixture.componentsInput.setValue(base, mixture);
        var value = real(0.0);
        mixture.paramInput.setValue(value, mixture);
        assertThrows(IllegalArgumentException.class, mixture::initAndValidate);
        base.paramInput.setValue(real(0.0), base);
        assertThrows(IllegalArgumentException.class, mixture::initAndValidate);
    }

    @Test
    void emptyComponentsAndMissingWeightsAreRejected() {
        var mixture = new NormalMixtureDistribution();
        assertThrows(IllegalArgumentException.class, mixture::validateMixture);
        mixture.componentsInput.setValue(normal(real(0.0), positive(1.0)), mixture);
        assertThrows(IllegalArgumentException.class, mixture::validateMixture);
    }

    @Test
    void hierarchicalSamplingIsExplicitlyUnsupportedInsteadOfSkippingComponentConditions() {
        var mixture = mixture(List.of(normal(real(0.0), positive(1.0))), simplex(1.0), real(0.0));
        assertThrows(UnsupportedOperationException.class, () -> mixture.sample(null, new Random(1)));
    }

    private static NormalMixtureDistribution mixture(
            List<Normal> components, Simplex weights, RealScalarParam<Real> value) {
        var mixture = new NormalMixtureDistribution();
        for (Normal component : components) {
            component.paramInput.setValue(value, component);
            mixture.componentsInput.setValue(component, mixture);
        }
        mixture.weightsInput.setValue(weights, mixture);
        mixture.paramInput.setValue(value, mixture);
        mixture.initAndValidate();
        return mixture;
    }

    private static Normal normal(RealScalarParam<Real> mean, RealScalarParam<PositiveReal> sd) {
        var normal = new Normal();
        normal.initByName("mean", mean, "sigma", sd);
        return normal;
    }

    private static RealScalarParam<Real> real(double value) {
        return new RealScalarParam<>(value, Real.INSTANCE);
    }

    private static RealScalarParam<PositiveReal> positive(double value) {
        return new RealScalarParam<>(value, PositiveReal.INSTANCE);
    }

    private static SimplexParam simplex(double... values) {
        return new SimplexParam(values);
    }

    private static double logNormal(double value, double mean, double sd) {
        double residual = (value - mean) / sd;
        return -0.5 * Math.log(2.0 * Math.PI) - Math.log(sd) - 0.5 * residual * residual;
    }

    private static double expected(
            double value, double firstMean, double firstSd, double secondMean, double secondSd, double firstWeight) {
        return Math.log(firstWeight * Math.exp(logNormal(value, firstMean, firstSd))
                + (1.0 - firstWeight) * Math.exp(logNormal(value, secondMean, secondSd)));
    }

    private static List<Double> samples(NormalMixtureDistribution distribution, int count) {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < count; i++) values.add(distribution.sample().getFirst());
        return values;
    }

    /** Allows malformed or resized weights to reach the distribution's own validation. */
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
