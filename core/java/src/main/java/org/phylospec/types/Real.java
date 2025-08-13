package org.phylospec.types;

/**
 * Real number type.
 * 
 * Represents a floating-point number that must be finite (not NaN or Infinity).
 * This is the base type for all continuous numeric types in PhyloSpec.
 * Note: this uses the fully qualified java.lang.Double when needed to avoid conflicts.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface Real extends Primitive<java.lang.Double> {
    /**
     * Get the numeric value.
     * 
     * @return the real number value
     */
    Double getPrimitive();
    
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
        return isReal(getPrimitive());
    }

    /**
     * {@inheritDoc}
     *
     * The logic to determine if a value is a {@link Real}.
     * This can be used by phylospec extensions.
     *
     * @param value  Java’s double
     * @return true if the value is {@link Real}, false otherwise
     */
    static boolean isReal(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
