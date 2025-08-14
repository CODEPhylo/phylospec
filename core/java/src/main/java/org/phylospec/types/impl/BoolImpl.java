package org.phylospec.types.impl;

import org.phylospec.types.Bool;

/**
 * Immutable implementation of the Boolean type.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class BoolImpl implements Bool {
    private final boolean value;
    
    /**
     * Constructs a Boolean with the given value.
     * 
     * @param value the boolean value
     */
    public BoolImpl(boolean value) {
        this.value = value;
    }
    
    @Override
    public java.lang.Boolean getPrimitive() {
        return java.lang.Boolean.valueOf(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Bool)) return false;
        Bool other = (Bool) obj;
        return value == other.getPrimitive();
    }
    
    @Override
    public int hashCode() {
        return java.lang.Boolean.hashCode(value);
    }
    
    @Override
    public String toString() {
        return String.format("%s(%s)", getTypeName(), value);
    }
}
