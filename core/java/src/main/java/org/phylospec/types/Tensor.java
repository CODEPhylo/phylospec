package org.phylospec.types;

import org.phylospec.domain.Domain;

/**
 * Tensor type - ordered value or values.
 *
 * @param <D> the type of {@link Domain <T>}.
 * @param <T> the primitive type in Java.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface Tensor<D extends Domain<T>, T> {

    /**
     * The order or degree of a tensor.
     * For example, Scalar → rank = 0 (no indices),
     * Vector → rank = 1 (needs 1 index),
     * Matrix → rank = 2 (needs 2 indices).
     *
     * @return  the number of indices needed to uniquely identify one of its elements.
     */
    int rank();

    /**
     * The size(s) along each dimension.
     *
     * @return empty for Scalar, one integer for Vector, two integers for Matrix, e.g. [3,4].
     */
    int[] shape();

    /**
     * Get the primitive value with the type T.
     *
     * @param idx  index/indices, depending on the dimension
     * @return  the primitive value with the type T in Java.
     */
    T get(int... idx);

    /**
     * Get the domain type D.
     *
     * @return the domain value
     */
    D domainType();

    default long size(){
        long s=1;
        for(int d:shape())
            s*=d;
        return s;
    }

    /**
     * Validate that this instance satisfies the type constraints.
     *
     * @return true if this instance is valid according to its type constraints, false otherwise
     */
    boolean isValid();
}