package org.phylospec.types.impl;

import org.phylospec.types.Probability;
import org.phylospec.types.Simplex;
import java.util.*;

/**
 * Immutable implementation of the Simplex type.
 * A Simplex is a probability vector whose elements sum to 1.0.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class SimplexImpl extends VectorImpl<Probability> implements Simplex {
    
    /**
     * Constructs a Simplex from a list of probabilities.
     * 
     * @param probabilities the probability values
     * @throws IllegalArgumentException if the values don't sum to 1.0 or list is empty
     */
    public SimplexImpl(List<Probability> probabilities) {
        super(probabilities);
        if (!isValid()) {
            double sum = 0.0;
            for (Probability p : probabilities) {
                sum += p.getPrimitive();
            }
            throw new IllegalArgumentException(
                String.format("Simplex elements must sum to 1.0 (within tolerance 1e-10), but sum was: %f", sum));
        }
    }
    
    /**
     * Constructs a Simplex from probability values.
     * 
     * @param values the probability values
     * @throws IllegalArgumentException if the values don't sum to 1.0
     */
    public SimplexImpl(double... values) {
        this(createProbabilities(values));
    }
    
    /**
     * Constructs a Simplex from an array of probabilities.
     * 
     * @param probabilities the probability values
     * @throws IllegalArgumentException if the values don't sum to 1.0
     */
    public SimplexImpl(Probability... probabilities) {
        this(Arrays.asList(probabilities));
    }
    
    private static List<Probability> createProbabilities(double[] values) {
        List<Probability> probs = new ArrayList<>(values.length);
        for (double v : values) {
            probs.add(new ProbabilityImpl(v));
        }
        return probs;
    }
    
    @Override
    public String getTypeName() {
        return "Simplex";
    }
}
