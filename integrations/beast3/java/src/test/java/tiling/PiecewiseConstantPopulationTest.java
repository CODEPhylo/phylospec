package tiling;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.type.RealVector;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PiecewiseConstantPopulationTest {
    private static final double TOLERANCE = 1e-12;

    @Test
    void populationSizesAreRightContinuousAtTheFixedChangeTimes() {
        var population = population(vector(100.0, 500.0, 200.0), 1.0, 3.0);

        assertEquals(100.0, population.getPopSize(0.0));
        assertEquals(100.0, population.getPopSize(Math.nextDown(1.0)));
        assertEquals(500.0, population.getPopSize(1.0));
        assertEquals(500.0, population.getPopSize(Math.nextDown(3.0)));
        assertEquals(200.0, population.getPopSize(3.0));
        assertEquals(200.0, population.getPopSize(100.0));
    }

    @Test
    void intensityAndIntegralMatchHandCalculatedEpochContributions() {
        var population = population(vector(100.0, 500.0, 200.0), 1.0, 3.0);

        assertEquals(0.0, population.getIntensity(0.0), 0.0);
        assertEquals(0.005, population.getIntensity(0.5), TOLERANCE);
        assertEquals(0.01, population.getIntensity(1.0), TOLERANCE);
        assertEquals(0.012, population.getIntensity(2.0), TOLERANCE);
        assertEquals(0.014, population.getIntensity(3.0), TOLERANCE);
        assertEquals(0.024, population.getIntensity(5.0), TOLERANCE);
        // Half of the first epoch, all of the second, and one unit of the third.
        assertEquals(0.5 / 100.0 + 2.0 / 500.0 + 1.0 / 200.0, population.getIntegral(0.5, 4.0), TOLERANCE);
        assertEquals(2.0 / 500.0, population.getIntegral(1.0, 3.0), TOLERANCE);
        assertEquals(0.0, population.getIntegral(3.0, 3.0), 0.0);
    }

    @Test
    void inverseIntensityMatchesHandCalculatedValuesAndRoundTripsAcrossEpochs() {
        var population = population(vector(100.0, 500.0, 200.0), 1.0, 3.0);

        assertEquals(0.0, population.getInverseIntensity(0.0), 0.0);
        assertEquals(0.5, population.getInverseIntensity(0.005), TOLERANCE);
        assertEquals(1.0, population.getInverseIntensity(0.01), TOLERANCE);
        assertEquals(2.0, population.getInverseIntensity(0.012), TOLERANCE);
        assertEquals(3.0, population.getInverseIntensity(0.014), TOLERANCE);
        assertEquals(5.0, population.getInverseIntensity(0.024), TOLERANCE);
        for (double time : new double[] {0.0, 0.1, 0.9, 1.0, 1.1, 2.9, 3.0, 3.1, 100.0}) {
            assertEquals(time, population.getInverseIntensity(population.getIntensity(time)), TOLERANCE);
        }
    }

    @Test
    void onePopulationWithNoChangeTimesIsTheConstantPopulationCase() {
        var population = population(vector(40.0));

        assertEquals(40.0, population.getPopSize(0.0));
        assertEquals(40.0, population.getPopSize(100.0));
        assertEquals(7.0 / 40.0, population.getIntensity(7.0), TOLERANCE);
        assertEquals(5.0 / 40.0, population.getIntegral(2.0, 7.0), TOLERANCE);
        assertEquals(7.0, population.getInverseIntensity(7.0 / 40.0), TOLERANCE);
    }

    @Test
    void directIntegralRetainsShortIntervalsAtLargeTimes() {
        var population = population(vector(100.0, 500.0, 200.0), 1.0, 3.0);
        double start = 1e16;
        double finish = Math.nextUp(start);

        assertEquals((finish - start) / 200.0, population.getIntegral(start, finish), 0.0);
    }

    @Test
    void populationValuesAndChangeTimesRemainLive() {
        var sizes = vector(100.0, 500.0, 200.0);
        var times = vector(1.0, 3.0);
        var population = population(sizes, times);

        assertSame(sizes, population.populationSizesInput.get());
        assertSame(times, population.changeTimesInput.get());
        assertTrue(sizes.getOutputs().contains(population));
        assertTrue(times.getOutputs().contains(population));
        assertEquals(500.0, population.getPopSize(1.5));

        times.set(0, 2.0);
        assertEquals(100.0, population.getPopSize(1.5));
        assertEquals(500.0, population.getPopSize(2.0));
        assertEquals(2.0 / 100.0 + 1.0 / 500.0, population.getIntensity(3.0), TOLERANCE);
        assertEquals(1.5 / 100.0 + 1.0 / 500.0 + 1.0 / 200.0, population.getIntegral(0.5, 4.0), TOLERANCE);
        assertEquals(3.0, population.getInverseIntensity(0.022), TOLERANCE);

        times.set(1, 4.0);
        assertEquals(500.0, population.getPopSize(3.5));
        assertEquals(200.0, population.getPopSize(4.0));
        assertEquals(2.0 / 100.0 + 2.0 / 500.0 + 1.0 / 200.0, population.getIntensity(5.0), TOLERANCE);
        assertEquals(4.0, population.getInverseIntensity(0.024), TOLERANCE);

        sizes.set(0, 50.0);
        assertEquals(50.0, population.getPopSize(0.5));
        assertEquals(0.04 + 0.004, population.getIntensity(4.0), TOLERANCE);
        assertEquals(2.0, population.getInverseIntensity(0.04), TOLERANCE);
        sizes.set(1, 250.0);
        assertEquals(250.0, population.getPopSize(2.0));
        assertEquals(0.04 + 0.008, population.getIntensity(4.0), TOLERANCE);
    }

    @Test
    void initialDimensionsMustDescribeAtLeastOnePopulationAndExactlyItsBoundaries() {
        var empty = vector(1.0);
        empty.setDimension(0);
        assertThrows(IllegalArgumentException.class, () -> population(empty));
        assertThrows(IllegalArgumentException.class, () -> population(vector(100.0, 200.0)));
        assertThrows(IllegalArgumentException.class, () -> population(vector(100.0), 1.0));
        assertThrows(IllegalArgumentException.class, () -> population(vector(100.0, 200.0), 1.0, 2.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void initialChangeTimesMustBeFiniteAndPositive(double invalid) {
        assertThrows(IllegalArgumentException.class, () -> population(vector(100.0, 200.0), invalid));
    }

    @Test
    void changeTimesMustBeStrictlyIncreasing() {
        assertThrows(IllegalArgumentException.class, () -> population(vector(100.0, 200.0, 300.0), 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> population(vector(100.0, 200.0, 300.0), 2.0, 1.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void invalidInitialPopulationValuesAreRejected(double invalid) {
        var sizes = new UncheckedVector(100.0, 200.0);
        sizes.replaceUnchecked(1, invalid);
        assertThrows(IllegalArgumentException.class, () -> population(sizes, 1.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void runtimePopulationValidityTracksTheCurrentValues(double invalid) {
        var sizes = new UncheckedVector(100.0, 200.0);
        var population = population(sizes, 1.0);
        assertTrue(population.hasValidPopulationSizes());

        sizes.replaceUnchecked(1, invalid);
        assertFalse(population.hasValidPopulationSizes());
        sizes.replaceUnchecked(1, 200.0);
        assertTrue(population.hasValidPopulationSizes());
        assertEquals(200.0, population.getPopSize(2.0));
        assertEquals(0.01 + 0.005, population.getIntensity(2.0), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void runtimeChangeTimeValidityTracksTheCurrentValuesAndRecovers(double invalid) {
        var times = new UncheckedVector(1.0, 3.0);
        var population = population(vector(100.0, 500.0, 200.0), times);
        assertTrue(population.hasValidChangeTimes());

        times.replaceUnchecked(0, invalid);
        assertFalse(population.hasValidChangeTimes());
        times.replaceUnchecked(0, 1.0);
        assertTrue(population.hasValidChangeTimes());
        assertEquals(500.0, population.getPopSize(2.0));
        assertEquals(0.014, population.getIntensity(3.0), TOLERANCE);
        assertEquals(3.0, population.getInverseIntensity(0.014), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(doubles = {3.0, 4.0})
    void coincidentOrReversedTimeProposalsAreInvalidAndRecover(double proposed) {
        var times = vector(1.0, 3.0);
        var population = population(vector(100.0, 500.0, 200.0), times);

        times.set(0, proposed);
        assertFalse(population.hasValidChangeTimes());
        times.set(0, 1.0);
        assertTrue(population.hasValidChangeTimes());
        assertEquals(0.014, population.getIntensity(3.0), TOLERANCE);
    }

    @Test
    void changingThePopulationVectorLengthIsExplicitlyUnsupported() {
        var sizes = vector(100.0, 200.0);
        var population = population(sizes, 1.0);
        sizes.setDimension(3);

        assertThrows(IllegalArgumentException.class, population::hasValidPopulationSizes);
    }

    @Test
    void changingTheTimeVectorLengthIsExplicitlyUnsupported() {
        var times = vector(1.0);
        var population = population(vector(100.0, 200.0), times);
        times.setDimension(2);

        assertThrows(IllegalArgumentException.class, population::hasValidChangeTimes);
    }

    @Test
    void parameterIdsIncludePopulationAndTimeVectors() {
        var sizes = vector(100.0, 200.0);
        sizes.setID("sizes");
        var times = vector(1.0);
        times.setID("times");

        assertEquals(List.of("sizes", "times"), population(sizes, times).getParameterIds());
    }

    @Test
    void scalarListConditionsIncludeTimesAndDoNotDuplicateSharedParameters() {
        var recent = new RealScalarParam<>(100.0, Real.INSTANCE);
        recent.setID("recent");
        var shared = new RealScalarParam<>(200.0, Real.INSTANCE);
        shared.setID("shared");
        var sizes = new RealVectorFromScalars();
        sizes.valuesInput.setValue(List.of(recent, shared), sizes);
        sizes.initAndValidate();
        var times = new RealVectorFromScalars();
        times.valuesInput.setValue(List.of(shared), times);
        times.initAndValidate();

        assertEquals(List.of("recent", "shared"), population(sizes, times).getParameterIds());
    }

    private static RealVectorParam<PositiveReal> vector(double... sizes) {
        return new RealVectorParam<>(sizes, PositiveReal.INSTANCE);
    }

    private static PiecewiseConstantPopulation population(RealVector<? extends Real> sizes, double... times) {
        return population(sizes, new UncheckedVector(times));
    }

    private static PiecewiseConstantPopulation population(
            RealVector<? extends Real> sizes, RealVector<? extends Real> times) {
        var population = new PiecewiseConstantPopulation();
        population.populationSizesInput.setValue(sizes, population);
        population.changeTimesInput.setValue(times, population);
        population.initAndValidate();
        return population;
    }

    // Derived values can violate their declared domain before the posterior rejects a proposal.
    // Bypass native parameter validation here to exercise the population function's own guard.
    private static class UncheckedVector extends RealVectorParam<Real> {
        UncheckedVector(double... initial) {
            super(new double[] {1.0}, Real.INSTANCE);
            setDimension(initial.length);
            for (int i = 0; i < initial.length; i++) replaceUnchecked(i, initial[i]);
        }

        void replaceUnchecked(int index, double value) {
            values[index] = value;
        }
    }
}
