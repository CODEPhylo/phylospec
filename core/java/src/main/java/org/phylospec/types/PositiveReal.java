package org.phylospec.types;

/**
 * Positive real number type (> 0).
 * 
 * Represents a real number that must be strictly positive.
 * Common uses include rates, branch lengths, and variance parameters.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface PositiveReal extends NonNegativeReal {
    /**
     * {@inheritDoc}
     * 
     * @return "PositiveReal"
     */
    @Override
    default java.lang.String getTypeName() {
        return "PositiveReal";
    }
    
    /**
     * {@inheritDoc}
     * 
     * A PositiveReal is valid if it is finite and strictly positive (> 0).
     * 
     * @return true if the value is finite and positive, false otherwise
     */
    @Override
    default boolean isValid() {
        return Real.isReal(getPrimitive()) && getPrimitive() > 0;
    }
}
