package org.phylospec.types;

/**
 * Non-negative real number type (>= 0).
 * 
 * Represents a real number that must be non-negative.
 * Common uses include distances, time durations, and count data.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface NonNegativeReal extends Real {
    /**
     * {@inheritDoc}
     * 
     * @return "NonNegativeReal"
     */
    @Override
    default java.lang.String getTypeName() {
        return "NonNegativeReal";
    }
    
    /**
     * {@inheritDoc}
     * 
     * A NonNegativeReal is valid if it is finite and non-negative (>= 0).
     * 
     * @return true if the value is finite and non-negative, false otherwise
     */
    @Override
    default boolean isValid() {
        return Real.isReal(getPrimitive()) && getPrimitive() >= 0;
    }
}
