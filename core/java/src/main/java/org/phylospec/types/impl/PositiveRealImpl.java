package org.phylospec.types.impl;

import org.phylospec.types.PositiveReal;

/**
 * Immutable implementation of the PositiveReal type.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class PositiveRealImpl implements PositiveReal {
    private final double value;
    
    /**
     * Constructs a PositiveReal with the given value.
     * 
     * @param value the positive real number value
     * @throws IllegalArgumentException if value is not positive or not finite
     */
    public PositiveRealImpl(double value) {
        this.value = value;
        if (!isValid()) {
            throw new IllegalArgumentException(
                "PositiveReal value must be finite and > 0, but was: " + value);
        }
    }
    
    @Override
    public double getValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PositiveReal)) return false;
        PositiveReal other = (PositiveReal) obj;
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
