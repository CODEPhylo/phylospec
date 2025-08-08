package org.phylospec.types.impl;

import org.phylospec.types.String;
import java.util.Objects;

/**
 * Immutable implementation of the String type.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public final class StringImpl implements String {
    private final java.lang.String value;
    
    /**
     * Constructs a String with the given value.
     * 
     * @param value the string value
     * @throws IllegalArgumentException if value is null
     */
    public StringImpl(java.lang.String value) {
        this.value = value;
        if (!isValid()) {
            throw new IllegalArgumentException("String value cannot be null");
        }
    }
    
    @Override
    public java.lang.String getValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof String)) return false;
        String other = (String) obj;
        return Objects.equals(value, other.getValue());
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
