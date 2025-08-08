package org.phylospec.types;

import java.util.List;

/**
 * Vector type - ordered collection of values.
 * 
 * Represents a one-dimensional array of elements of the same type.
 * Used for parameter vectors, frequency distributions, and data arrays.
 * 
 * @param <T> the type of elements in this vector
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface Vector<T extends PhyloSpecType> extends PhyloSpecType {
    /**
     * Get all elements in the vector.
     * 
     * @return an unmodifiable list of all elements
     */
    List<T> getElements();
    
    /**
     * Get the number of elements in the vector.
     * 
     * @return the size of the vector
     */
    int size();
    
    /**
     * Get the element at the specified index.
     * 
     * @param index the index of the element to retrieve (0-based)
     * @return the element at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    T get(int index);
    
    /**
     * {@inheritDoc}
     * 
     * @return "Vector"
     */
    @Override
    default String getTypeName() {
        return "Vector";
    }
    
    /**
     * {@inheritDoc}
     * 
     * A Vector is valid if it is not null and all its elements are valid.
     * 
     * @return true if all elements are valid, false otherwise
     */
    @Override
    default boolean isValid() {
        return getElements() != null && 
               getElements().stream().allMatch(PhyloSpecType::isValid);
    }
}
