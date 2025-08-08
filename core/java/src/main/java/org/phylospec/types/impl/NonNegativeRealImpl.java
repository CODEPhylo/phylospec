package org.phylospec.types.impl;

import org.phylospec.types.NonNegativeReal;

/**
 * Immutable implementation of the NonNegativeReal type.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class NonNegativeRealImpl implements NonNegativeReal {
    private final double value;
    
    /**
     * Constructs a NonNegativeReal with the given value.
     * 
     * @param value the non-negative real number value
     * @throws IllegalArgumentException if value is negative or not finite
     */
    public NonNegativeRealImpl(double value) {
        this.value = value;
        if (!isValid()) {
            throw new IllegalArgumentException(
                "NonNegativeReal value must be finite and >= 0, but was: " + value);
        }
    }
    
    @Override
    public double getValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NonNegativeReal)) return false;
        NonNegativeReal other = (NonNegativeReal) obj;
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
