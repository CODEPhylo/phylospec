package org.phylospec.types;

/**
 * Base interface for all PhyloSpec types.
 * 
 * This interface provides the foundation for the PhyloSpec type system,
 * ensuring all types can validate themselves and provide type information.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface PhyloSpecType<T> {

    /**
     * Validate that this instance satisfies the type constraints.
     * 
     * @return true if this instance is valid according to its type constraints, false otherwise
     */
    boolean isValid();
    
    /**
     * Get a string representation of the type.
     * 
     * @return the name of this type (e.g., "Real", "PositiveInteger", "Simplex")
     */
    java.lang.String getTypeName();
}
