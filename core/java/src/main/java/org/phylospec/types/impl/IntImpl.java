package org.phylospec.types.impl;

import org.phylospec.types.Int;

/**
 * Immutable implementation of the Integer type.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class IntImpl implements Int {
    private final int value;
    
    /**
     * Constructs an Integer with the given value.
     * 
     * @param value the integer value
     */
    public IntImpl(int value) {
        this.value = value;
    }
    
    @Override
    public java.lang.Integer getPrimitive() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Int)) return false;
        Int other = (Int) obj;
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
