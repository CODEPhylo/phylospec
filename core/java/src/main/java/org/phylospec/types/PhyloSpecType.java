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
@Deprecated
public interface PhyloSpecType<T> {

    /**
     * Get a string representation of the type.
     * 
     * @return the name of this type (e.g., "Real", "PositiveInteger", "Simplex")
     */
    java.lang.String getTypeName();
}
