package tiling;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.core.BEASTObject;
import beast.base.spec.domain.Real;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.Bernoulli;
import beast.base.spec.inference.parameter.BoolScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beast.base.util.Randomizer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BernoulliDistributionTest {
    private static final double TOLERANCE = 1e-12;

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.2, 0.5, 0.8, 1.0})
    void booleanProbabilitiesMatchNativeBeastForBothOutcomes(double probability) {
        RealScalarParam<UnitInterval> p = new RealScalarParam<>(probability, UnitInterval.INSTANCE);
        BoolScalarParam value = new BoolScalarParam(false);
        BernoulliDistribution distribution = distribution(p, value);
        Bernoulli nativeDistribution = new Bernoulli(value, p);
        assertEquals(probability >= 0.5, distribution.mostLikelyValue());
        for (boolean outcome : new boolean[] {false, true}) {
            value.set(outcome);
            double expected = outcome ? Math.log(probability) : Math.log1p(-probability);
            assertEquals(expected, distribution.calculateLogP(), TOLERANCE);
            assertEquals(nativeDistribution.calculateLogP(), distribution.calculateLogP(), TOLERANCE);
        }
    }

    @Test
    void retainsSharedInputAndArgumentIdentityWithoutACachedProbability() {
        RealScalarParam<Real> p = new RealScalarParam<>(0.2, Real.INSTANCE);
        p.setID("probability");
        BoolScalarParam value = new BoolScalarParam(true);
        value.setID("outcome");
        BernoulliDistribution distribution = distribution(p, value);
        assertSame(p, distribution.pInput.get());
        assertSame(value, distribution.paramInput.get());
        assertEquals(List.of("probability"), distribution.getConditions());
        assertEquals(List.of("outcome"), distribution.getArguments());
        assertEquals(Math.log(0.2), distribution.calculateLogP(), TOLERANCE);

        p.set(0.8);
        assertEquals(Math.log(0.8), distribution.calculateLogP(), TOLERANCE);
        value.set(false);
        assertEquals(Math.log1p(-0.8), distribution.calculateLogP(), TOLERANCE);
        // A smaller-than-native-EPS change must not reuse the previous probability.
        p.set(1e-13);
        assertEquals(Math.log1p(-1e-13), distribution.calculateLogP(), 0.0);
        p.set(2e-13);
        assertEquals(Math.log1p(-2e-13), distribution.calculateLogP(), 0.0);
    }

    @Test
    void samplesReadChangedInputsWithoutAnInterveningDensityEvaluation() {
        RealScalarParam<Real> p = new RealScalarParam<>(0.0, Real.INSTANCE);
        BernoulliDistribution distribution = distribution(p, new BoolScalarParam(false));
        for (double probability : new double[] {0.0, 1.0, 0.0, 1.0}) {
            p.set(probability);
            for (int i = 0; i < 10; i++) {
                assertEquals(List.of(probability == 1.0), distribution.sample());
            }
        }
        // Use the same seeded RNG as native BEAST, without relying on a statistical tolerance.
        p.set(0.3);
        Randomizer.setSeed(7319);
        boolean expected = Randomizer.nextDouble() < 0.3;
        Randomizer.setSeed(7319);
        assertEquals(List.of(expected), distribution.sample());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void rejectsInvalidInitialProbabilitiesButScoresInvalidProposalsAsZeroMass(double invalid) {
        MutableReal p = new MutableReal(invalid);
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> distribution(p, new BoolScalarParam(false)));
        assertTrue(error.getMessage().contains("[0, 1]"), error.getMessage());

        p.value = 0.4;
        BoolScalarParam value = new BoolScalarParam(false);
        BernoulliDistribution distribution = distribution(p, value);
        p.value = invalid;
        for (boolean outcome : new boolean[] {false, true}) {
            value.set(outcome);
            assertEquals(Double.NEGATIVE_INFINITY, distribution.calculateLogP());
        }
        assertThrows(IllegalArgumentException.class, distribution::sample);
        p.value = 0.4;
        assertEquals(Math.log(0.4), distribution.calculateLogP(), TOLERANCE);
        assertTrue(distribution.getConditions().isEmpty());
    }

    @Test
    void requiresAProbabilityAndDoesNotInventNumericBoundsForBooleanValues() {
        BernoulliDistribution distribution = new BernoulliDistribution();
        assertThrows(IllegalArgumentException.class, distribution::initAndValidate);
        assertThrows(IllegalStateException.class, distribution::getLowerBoundOfParameter);
        assertThrows(IllegalStateException.class, distribution::getUpperBoundOfParameter);
    }

    private static BernoulliDistribution distribution(RealScalar<?> p, BoolScalarParam value) {
        BernoulliDistribution distribution = new BernoulliDistribution();
        distribution.pInput.setValue(p, distribution);
        distribution.paramInput.setValue(value, distribution);
        distribution.initAndValidate();
        return distribution;
    }

    private static final class MutableReal extends BEASTObject implements RealScalar<Real> {
        private double value;

        private MutableReal(double value) {
            this.value = value;
        }

        @Override
        public void initAndValidate() {}

        @Override
        public double get() {
            return value;
        }

        @Override
        public Real getDomain() {
            return Real.INSTANCE;
        }
    }
}
