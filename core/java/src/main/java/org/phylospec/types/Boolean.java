package org.phylospec.types;

/**
 * Boolean type.
 * 
 * Represents a true/false value.
 * Used for binary switches, flags, and binary character data.
 * 
 * Note: This interface is named Boolean to match PhyloSpec conventions,
 * but uses the fully qualified java.lang.Boolean when needed to avoid conflicts.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface Boolean extends PhyloSpecType {
    /**
     * Get the boolean value.
     * 
     * @return the boolean value
     */
    boolean getValue();
    
    /**
     * {@inheritDoc}
     * 
     * @return "Boolean"
     */
    @Override
    default String getTypeName() {
        return "Boolean";
    }
    
    /**
     * {@inheritDoc}
     * 
     * All boolean values are considered valid.
     * 
     * @return always true
     */
    @Override
    default boolean isValid() {
        return true;
    }
}
