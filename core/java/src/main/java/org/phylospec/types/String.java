package org.phylospec.types;

/**
 * String type.
 * 
 * Represents a text value.
 * Used for taxon names, model identifiers, and other textual data.
 * 
 * Note: This interface is named String to match PhyloSpec conventions,
 * but uses the fully qualified java.lang.String when needed to avoid conflicts.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface String extends Primitive<java.lang.String> {
    /**
     * Get the string value.
     * 
     * @return the string value
     */
    java.lang.String getPrimitive();
    
    /**
     * {@inheritDoc}
     * 
     * @return "String"
     */
    @Override
    default java.lang.String getTypeName() {
        return "String";
    }
    
    /**
     * {@inheritDoc}
     * 
     * A String is valid if it is not null.
     * 
     * @return true if the value is not null, false otherwise
     */
    @Override
    default boolean isValid() {
        return getPrimitive() != null;
    }
}
