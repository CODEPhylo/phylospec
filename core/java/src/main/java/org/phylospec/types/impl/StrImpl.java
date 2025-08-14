package org.phylospec.types.impl;

import org.phylospec.types.Str;

import java.util.Objects;

/**
 * Immutable implementation of the String type.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class StrImpl implements Str {
    private final java.lang.String value;
    
    /**
     * Constructs a String with the given value.
     * 
     * @param value the string value
     * @throws IllegalArgumentException if value is null
     */
    public StrImpl(java.lang.String value) {
        this.value = value;
        if (!isValid()) {
            throw new IllegalArgumentException("String value cannot be null");
        }
    }
    
    @Override
    public java.lang.String getPrimitive() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Str)) return false;
        Str other = (Str) obj;
        return Objects.equals(value, other.getPrimitive());
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
    
    @Override
    public java.lang.String toString() {
        return java.lang.String.format("%s(\"%s\")", getTypeName(), value);
    }
}
