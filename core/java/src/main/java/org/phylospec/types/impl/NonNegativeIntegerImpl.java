package org.phylospec.types.impl;

import org.phylospec.types.NonNegativeInteger;

/**
 * Immutable implementation of the NonNegativeInteger type.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class NonNegativeIntegerImpl implements NonNegativeInteger {
    private final int value;

    /**
     * Constructs a NonNegativeInteger with the given value.
     *
     * @param value the non-negative integer number value
     * @throws IllegalArgumentException if value is negative or not finite
     */
    public NonNegativeIntegerImpl(int value) {
        this.value = value;
        if (!isValid()) {
            throw new IllegalArgumentException(
                "NonNegativeInteger value must be finite and >= 0, but was: " + value);
        }
    }
    
    @Override
    public Integer getPrimitive() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NonNegativeInteger)) return false;
        NonNegativeInteger other = (NonNegativeInteger) obj;
        return Double.compare(value, other.getPrimitive()) == 0;
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
