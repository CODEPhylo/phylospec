package org.phylospec.types;

/**
 * Probability type (value in [0, 1]).
 * 
 * Represents a probability value that must be between 0 and 1 inclusive.
 * Used for transition probabilities, mixture weights, and other probabilistic parameters.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface Probability extends Real {
    /**
     * {@inheritDoc}
     * 
     * @return "Probability"
     */
    @Override
    default String getTypeName() {
        return "Probability";
    }
    
    /**
     * {@inheritDoc}
     * 
     * A Probability is valid if it is finite and in the range [0, 1].
     * 
     * @return true if the value is a valid probability, false otherwise
     */
    @Override
    default boolean isValid() {
        return Real.super.isValid() && getValue() >= 0.0 && getValue() <= 1.0;
    }
}
