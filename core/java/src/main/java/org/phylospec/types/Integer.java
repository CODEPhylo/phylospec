package org.phylospec.types;

/**
 * Integer type.
 * 
 * Represents a whole number (positive, negative, or zero).
 * Note: This interface is named Integer to match PhyloSpec conventions,
 * but uses the fully qualified java.lang.Integer when needed to avoid conflicts.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface Integer extends PhyloSpecType {
    /**
     * Get the integer value.
     * 
     * @return the integer value
     */
    int getValue();
    
    /**
     * {@inheritDoc}
     * 
     * @return "Integer"
     */
    @Override
    default String getTypeName() {
        return "Integer";
    }
    
    /**
     * {@inheritDoc}
     * 
     * All integer values are considered valid.
     * 
     * @return always true
     */
    @Override
    default boolean isValid() {
        return true;
    }
}
