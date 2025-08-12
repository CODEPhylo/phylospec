package org.phylospec.types;

/**
 * Real number type.
 * 
 * Represents a floating-point number that must be finite (not NaN or Infinity).
 * This is the base type for all continuous numeric types in PhyloSpec.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface Real extends PhyloSpecType {
    /**
     * Get the numeric value.
     * 
     * @return the real number value
     */
    double getValue();
    
    /**
     * {@inheritDoc}
     * 
     * @return "Real"
     */
    @Override
    default java.lang.String getTypeName() {
        return "Real";
    }
    
    /**
     * {@inheritDoc}
     * 
     * A Real is valid if it is finite (not NaN or Infinity).
     * 
     * @return true if the value is finite, false otherwise
     */
    @Override
    default boolean isValid() {
        return !Double.isNaN(getValue()) && !Double.isInfinite(getValue());
    }
}
