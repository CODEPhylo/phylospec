package org.phylospec.types.impl;

import org.phylospec.types.Boolean;

/**
 * Immutable implementation of the Boolean type.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class BooleanImpl implements Boolean {
    private final boolean value;
    
    /**
     * Constructs a Boolean with the given value.
     * 
     * @param value the boolean value
     */
    public BooleanImpl(boolean value) {
        this.value = value;
    }
    
    @Override
    public java.lang.Boolean getPrimitive() {
        return java.lang.Boolean.valueOf(value);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Boolean)) return false;
        Boolean other = (Boolean) obj;
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
