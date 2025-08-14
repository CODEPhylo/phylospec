package org.phylospec.types;

import java.lang.String;

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
public interface Bool extends Primitive<java.lang.Boolean> {
    /**
     * Get the boolean value.
     * 
     * @return the boolean value
     */
    java.lang.Boolean getPrimitive();

    /**
     * {@inheritDoc}
     *
     * @return "Bool"
     */
    @Override
    default String getTypeName() {
        return "Bool";
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
