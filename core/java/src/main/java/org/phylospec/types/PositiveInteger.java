package org.phylospec.types;

/**
 * Positive integer type (> 0).
 * 
 * Represents an integer that must be strictly positive.
 * Common uses include population sizes, sample counts, and dimensions.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface PositiveInteger extends Integer {
    /**
     * {@inheritDoc}
     * 
     * @return "PositiveInteger"
     */
    @Override
    default java.lang.String getTypeName() {
        return "PositiveInteger";
    }
    
    /**
     * {@inheritDoc}
     * 
     * A PositiveInteger is valid if it is strictly positive (> 0).
     * 
     * @return true if the value is positive, false otherwise
     */
    @Override
    default boolean isValid() {
        return getValue() > 0;
    }
}
