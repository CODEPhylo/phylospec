package org.phylospec.types.impl;

import org.phylospec.types.Probability;

/**
 * Immutable implementation of the Probability type.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class ProbabilityImpl implements Probability {
    private final double value;
    
    /**
     * Constructs a Probability with the given value.
     * 
     * @param value the probability value
     * @throws IllegalArgumentException if value is not in [0, 1] or not finite
     */
    public ProbabilityImpl(double value) {
        this.value = value;
        if (!isValid()) {
            throw new IllegalArgumentException(
                "Probability value must be finite and in [0, 1], but was: " + value);
        }
    }
    
    @Override
    public double getValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Probability)) return false;
        Probability other = (Probability) obj;
        return Double.compare(value, other.getValue()) == 0;
    }
    
    @Override
    public int hashCode() {
        return Double.hashCode(value);
    }
    
    @Override
    public String toString() {
        return String.format("%s(%s)", getTypeName(), value);
    }
}
