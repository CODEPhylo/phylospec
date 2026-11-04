package tiling;

import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.InverseGamma;
import beast.base.spec.inference.distribution.OffsetReal;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.inference.distribution.TruncatedReal;
import beast.base.spec.type.RealScalar;
import beast.base.util.Randomizer;
import java.util.List;
import org.apache.commons.statistics.distribution.ContinuousDistribution;

/** A truncated native continuous law with stable interval probabilities and live parameters. */
public final class LiveTruncatedDistribution extends TruncatedReal {

    private NativeLawAccessor accessor;

    @Override
    public void initAndValidate() {
        refresh();
        validateInterval();
        param = paramInput.get();
        // Impossible observations remain data with zero density, rather than being changed or rejected here.
        if (param != null && !param.getDomain().isValid(param.get())) {
            throw new IllegalArgumentException("The truncated parameter must satisfy its real domain.");
        }
    }

    /** Checks that the current base law gives positive, representable mass to the interval. */
    public void validateInterval() {
        requireMass(window());
    }

    @Override
    public double logDensity(double value) {
        Window window = window();
        if (window.mass == 0.0 || Double.isNaN(value) || value < window.lower || value > window.upper) {
            return Double.NEGATIVE_INFINITY;
        }
        return window.law.logDensity(value) - Math.log(window.mass);
    }

    @Override
    public double cumulativeProbability(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("A truncated CDF argument must not be NaN.");
        }
        Window window = requireMass(window());
        if (value <= window.lower) return 0.0;
        if (value >= window.upper) return 1.0;
        return Math.max(0.0, Math.min(1.0, window.law.probability(window.lower, value) / window.mass));
    }

    @Override
    public Double inverseCumulativeProbability(double probability) {
        if (!Double.isFinite(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("A truncated quantile probability must be in [0, 1].");
        }
        Window window = requireMass(window());
        if (probability == 0.0) return window.lower;
        if (probability == 1.0) return window.upper;

        double lowerCdf = window.law.cumulativeProbability(window.lower);
        double upperSurvival = window.law.survivalProbability(window.upper);
        // Interpolate on the nearer probability tail, avoiding 1 - tinyProbability rounding to one.
        double value = lowerCdf <= upperSurvival
                ? window.law.inverseCumulativeProbability(Math.fma(probability, window.mass, lowerCdf))
                : window.law.inverseSurvivalProbability(Math.fma(1.0 - probability, window.mass, upperSurvival));
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("The truncated quantile cannot be represented as a finite real value.");
        }
        return Math.max(window.lower, Math.min(window.upper, value));
    }

    @Override
    public List<Double> sample() {
        double probability;
        do {
            probability = Randomizer.nextDouble();
        } while (probability == 0.0);
        return List.of(inverseCumulativeProbability(probability));
    }

    @Override
    public Double getLowerBoundOfParameter() {
        return Math.max(lower(), currentLaw().getSupportLowerBound());
    }

    @Override
    public Double getUpperBoundOfParameter() {
        return Math.min(upper(), currentLaw().getSupportUpperBound());
    }

    @Override
    public Double getLower() {
        return getLowerBoundOfParameter();
    }

    @Override
    public Double getUpper() {
        return getUpperBoundOfParameter();
    }

    private double lower() {
        return lowerInput.get() == null
                ? Double.NEGATIVE_INFINITY
                : lowerInput.get().get();
    }

    private double upper() {
        return upperInput.get() == null
                ? Double.POSITIVE_INFINITY
                : upperInput.get().get();
    }

    private Window window() {
        double lower = lower();
        double upper = upper();
        if (Double.isNaN(lower) || Double.isNaN(upper) || !(lower < upper)) {
            throw new IllegalArgumentException("Continuous truncation requires lower < upper.");
        }
        ContinuousDistribution law = currentLaw();
        lower = Math.max(lower, law.getSupportLowerBound());
        upper = Math.min(upper, law.getSupportUpperBound());
        if (!(lower < upper)) return new Window(law, lower, upper, 0.0);

        double mass = law.probability(lower, upper);
        if (!(mass > 0.0) || !Double.isFinite(mass)) {
            // Nonempty support does not imply zero mathematical probability when floating point underflows.
            throw new IllegalArgumentException(
                    "The truncation interval has nonempty support, but its probability mass cannot be represented "
                            + "accurately. Use a numerically resolvable interval or base parameterization.");
        }
        return new Window(law, lower, upper, mass);
    }

    private static Window requireMass(Window window) {
        if (window.mass == 0.0) {
            throw new IllegalArgumentException(
                    "The truncation interval has no positive probability mass under its base.");
        }
        return window;
    }

    private ContinuousDistribution currentLaw() {
        var base = distributionInput.get();
        if (base == null) throw new IllegalArgumentException("Truncated requires a base distribution.");
        // These native wrappers expose another distribution's Apache object, not their own law.
        if (base instanceof OffsetReal || base instanceof TruncatedReal || InverseGamma.class.isInstance(base)) {
            throw new IllegalArgumentException(
                    "Truncated currently requires a directly backed continuous base distribution; "
                            + "Offset, Truncated and InverseGamma bases are not supported.");
        }
        if (accessor == null || accessor.distributionInput.get() != base) {
            accessor = new NativeLawAccessor(base);
        }
        return accessor.current();
    }

    private record Window(ContinuousDistribution law, double lower, double upper, double mass) {}

    /** Native OffsetReal exposes its base's protected numerical law without reparameterizing it. */
    private static final class NativeLawAccessor extends OffsetReal {
        private NativeLawAccessor(ScalarDistribution<RealScalar<Real>, Double> base) {
            // The delegate is not registered as a sampled state or posterior factor.
            // Use the native setter with an owner so the base never acquires a null output.
            initByName("distribution", base);
        }

        private ContinuousDistribution current() {
            Object law = super.getApacheDistribution();
            if (!(law instanceof ContinuousDistribution continuous)) {
                throw new IllegalArgumentException("Truncated requires a base backed by a continuous probability law.");
            }
            return continuous;
        }
    }
}
