package org.phylospec.types.impl;

import org.phylospec.types.Real;

/**
 * Immutable implementation of the Real type.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class RealImpl implements Real {
    private final double value;
    
    /**
     * Constructs a Real with the given value.
     * 
     * @param value the real number value
     * @throws IllegalArgumentException if value is NaN or infinite
     */
    public RealImpl(double value) {
        this.value = value;
        if (!isValid()) {
            throw new IllegalArgumentException(
                "Real value must be finite, but was: " + value);
        }
    }
    
    @Override
    public double getValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Real)) return false;
        Real other = (Real) obj;
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
