package tiling;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.Simplex;
import beast.base.util.Randomizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** A fixed-size mixture of native Normal laws, all evaluated at the same scalar. */
public final class NormalMixtureDistribution extends Distribution {

    private static final double NORMALIZATION_TOLERANCE = 1e-10;

    public final Input<List<Normal>> componentsInput =
            new Input<>("components", "Normal components of the mixture.", new ArrayList<>(), Input.Validate.REQUIRED);
    public final Input<Simplex> weightsInput =
            new Input<>("weights", "Mixture probabilities in component order.", Input.Validate.REQUIRED);
    public final Input<RealScalar<Real>> paramInput =
            new Input<>("param", "The single value shared by all mixture components.", Input.Validate.REQUIRED);

    private int componentCount;

    /** Checks structure and current weights without caching their numerical values. */
    public void validateMixture() {
        List<Normal> components = componentsInput.get();
        Simplex weights = weightsInput.get();
        if (components == null
                || components.isEmpty()
                || components.stream().anyMatch(component -> component == null)) {
            throw new IllegalArgumentException("Mixture requires a nonempty list of Normal components.");
        }
        if (componentCount != 0 && componentCount != components.size()) {
            throw new IllegalArgumentException("The number of mixture components must remain fixed.");
        }
        if (weights == null || weights.size() != components.size()) {
            throw new IllegalArgumentException("Mixture weights and components must have the same length.");
        }
        double sum = 0.0;
        for (int i = 0; i < weights.size(); i++) {
            double weight = weights.get(i);
            if (!Double.isFinite(weight) || weight < 0.0 || weight > 1.0) {
                throw new IllegalArgumentException("Mixture weights must be finite values in [0, 1].");
            }
            sum += weight;
        }
        if (Math.abs(sum - 1.0) > NORMALIZATION_TOLERANCE) {
            throw new IllegalArgumentException("Mixture weights must sum to 1.");
        }
        componentCount = components.size();
    }

    @Override
    public void initAndValidate() {
        validateMixture();
        RealScalar<Real> value = paramInput.get();
        if (value == null || !value.getDomain().isValid(value.get())) {
            throw new IllegalArgumentException("Mixture requires a value satisfying its real domain.");
        }
        for (Normal component : componentsInput.get()) {
            if (component.paramInput.get() != value) {
                throw new IllegalArgumentException("All mixture components must be bound to the same value.");
            }
            // Components were created as unbound laws. Refresh the native cached param after binding.
            component.initAndValidate();
        }
    }

    @Override
    public double calculateLogP() {
        validateMixture();
        double[] terms = new double[componentCount];
        double maximum = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < componentCount; i++) {
            double weight = weightsInput.get().get(i);
            if (weight == 0.0) {
                terms[i] = Double.NEGATIVE_INFINITY;
                continue;
            }
            // Native calculateLogP uses the stable Normal log density and reads current mean/sd.
            terms[i] = Math.log(weight) + componentsInput.get().get(i).calculateLogP();
            if (Double.isNaN(terms[i])) {
                throw new IllegalArgumentException("A mixture component produced an undefined log density.");
            }
            maximum = Math.max(maximum, terms[i]);
        }
        if (!Double.isFinite(maximum)) {
            logP = maximum;
            return logP;
        }
        double sum = 0.0;
        for (double term : terms) sum += Math.exp(term - maximum);
        logP = maximum + Math.log(sum);
        return logP;
    }

    /** Draws one value conditional on the current weights and component parameters. */
    public List<Double> sample() {
        validateMixture();
        double draw = Randomizer.nextDouble();
        double cumulative = 0.0;
        int selected = -1;
        for (int i = 0; i < componentCount; i++) {
            double weight = weightsInput.get().get(i);
            if (weight == 0.0) continue;
            selected = i;
            cumulative += weight;
            if (draw < cumulative) break;
        }
        // A valid simplex can sum just below one through rounding; retain the last positive component.
        double probability;
        do {
            probability = Randomizer.nextDouble();
        } while (probability == 0.0);
        // Unlike Native Normal.sample(), this refreshes the law rather than keeping an old sampler.
        return List.of(componentsInput.get().get(selected).inverseCumulativeProbability(probability));
    }

    @Override
    public List<String> getArguments() {
        if (paramInput.get() instanceof BEASTInterface value && value.getID() != null) {
            return List.of(value.getID());
        }
        return List.of();
    }

    @Override
    public List<String> getConditions() {
        Set<String> conditions = new LinkedHashSet<>();
        addId(conditions, weightsInput.get());
        for (Normal component : componentsInput.get()) {
            addId(conditions, component.meanInput.get());
            addId(conditions, component.sdInput.get());
            addId(conditions, component.tauInput.get());
        }
        return List.copyOf(conditions);
    }

    private static void addId(Set<String> ids, Object value) {
        if (value instanceof BEASTInterface object && object.getID() != null) ids.add(object.getID());
    }

    @Override
    public void sample(State state, Random random) {
        // Native sampleConditions does not traverse component lists. Calling it here would not
        // correctly sample a hierarchy, and bound components must not become separate priors for y.
        throw new UnsupportedOperationException("Hierarchical prior-predictive sampling is not supported for Mixture; "
                + "sample() draws conditional on the current inputs.");
    }
}
