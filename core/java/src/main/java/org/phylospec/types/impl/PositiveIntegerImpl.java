package org.phylospec.types.impl;

import org.phylospec.types.PositiveInteger;

/**
 * Immutable implementation of the PositiveInteger type.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class PositiveIntegerImpl implements PositiveInteger {
    private final int value;
    
    /**
     * Constructs a PositiveInteger with the given value.
     * 
     * @param value the positive integer value
     * @throws IllegalArgumentException if value is not positive
     */
    public PositiveIntegerImpl(int value) {
        this.value = value;
        if (!isValid()) {
            throw new IllegalArgumentException(
                "PositiveInteger value must be > 0, but was: " + value);
        }
    }
    
    @Override
    public java.lang.Integer getPrimitive() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PositiveInteger)) return false;
        PositiveInteger other = (PositiveInteger) obj;
        return value == other.getPrimitive();
    }
    
    @Override
    public int hashCode() {
        return java.lang.Integer.hashCode(value);
    }
    
    @Override
    public String toString() {
        return String.format("%s(%d)", getTypeName(), value);
    }
}
