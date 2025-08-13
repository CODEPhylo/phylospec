package org.phylospec.types;

/**
 * Base interface for PhyloSpec types having primitive values.
 * 
 * This interface provides the primitive value for a {@link PhyloSpecType} .
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface Primitive<T> extends PhyloSpecType<T> {

    /**
     * Get the primitive value with the type T.
     * 
     * @return the primitive value
     */
    T getPrimitive();

    /**
     * Get the primitive type of the value
     *
     * @return the class of the primitive type
     */
    default Class getPrimitiveType() {
        return getPrimitive().getClass();
    }

}
