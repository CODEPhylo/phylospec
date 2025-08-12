package org.phylospec.types.impl;

import org.phylospec.types.Integer;

/**
 * Immutable implementation of the Integer type.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class IntegerImpl implements Integer {
    private final int value;
    
    /**
     * Constructs an Integer with the given value.
     * 
     * @param value the integer value
     */
    public IntegerImpl(int value) {
        this.value = value;
    }
    
    @Override
    public java.lang.Integer getValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Integer)) return false;
        Integer other = (Integer) obj;
        return value == other.getValue();
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
