package org.phylospec.types;

/**
 * Non-negative integer number type (>= 0).
 * 
 * Represents an integer number that must be non-negative.
 * Common uses include distances, time durations, and count data.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface NonNegativeInteger extends Integer {
    /**
     * {@inheritDoc}
     * 
     * @return "NonNegativeInteger"
     */
    @Override
    default java.lang.String getTypeName() {
        return "NonNegativeInteger";
    }
    
    /**
     * {@inheritDoc}
     * 
     * A NonNegativeInteger is valid if it is finite and non-negative (>= 0).
     * 
     * @return true if the value is finite and non-negative, false otherwise
     */
    @Override
    default boolean isValid() {
        return Integer.super.isValid() && getPrimitive() >= 0;
    }
}
