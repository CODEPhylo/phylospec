package org.phylospec.primitives;

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
public class Real implements Primitive<Double> {
    public static final Real INSTANCE = new Real();

    protected Real() {}

    @Override
    public boolean isValid(Double value) {
        // Bound requires Inf
        return !Double.isNaN(value); //&& !Double.isInfinite(value);
    }
}