package org.phylospec.types.impl;

import org.phylospec.types.PhyloSpecType;
import org.phylospec.types.Vector;
import java.util.*;

/**
 * Immutable implementation of the Vector type.
 * 
 * @param <T> the type of elements in this vector
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public class VectorImpl<T extends PhyloSpecType> implements Vector<T> {
    private final List<T> elements;
    
    /**
     * Constructs a Vector from a list of elements.
     * 
     * @param elements the elements for this vector
     * @throws IllegalArgumentException if elements is null or contains invalid elements
     */
    public VectorImpl(List<T> elements) {
        if (elements == null) {
            throw new IllegalArgumentException("Vector elements cannot be null");
        }
        this.elements = new ArrayList<>(elements);
        if (!isValid()) {
            throw new IllegalArgumentException("Vector contains invalid elements");
        }
    }
    
    /**
     * Constructs a Vector from an array of elements.
     * 
     * @param elements the elements for this vector
     * @throws IllegalArgumentException if any element is invalid
     */
    @SafeVarargs
    public VectorImpl(T... elements) {
        this(Arrays.asList(elements));
    }
    
    @Override
    public List<T> getElements() {
        return Collections.unmodifiableList(elements);
    }
    
    @Override
    public int size() {
        return elements.size();
    }
    
    @Override
    public T get(int index) {
        return elements.get(index);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vector)) return false;
        Vector<?> other = (Vector<?>) obj;
        return Objects.equals(elements, other.getElements());
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(elements);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getTypeName()).append("[");
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(elements.get(i).getValue());
        }
        sb.append("]");
        return sb.toString();
    }
}
