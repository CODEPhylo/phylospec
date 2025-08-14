package org.phylospec.types.impl;

import org.phylospec.types.PositiveInt;

/**
 * Immutable implementation of the PositiveInteger type.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class PositiveIntImpl implements PositiveInt {
    private final int value;
    
    /**
     * Constructs a PositiveInteger with the given value.
     * 
     * @param value the positive integer value
     * @throws IllegalArgumentException if value is not positive
     */
    public PositiveIntImpl(int value) {
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
        if (!(obj instanceof PositiveInt)) return false;
        PositiveInt other = (PositiveInt) obj;
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
