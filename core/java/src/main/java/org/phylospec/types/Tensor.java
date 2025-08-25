package org.phylospec.types;

import org.phylospec.primitives.Primitive;

import java.util.stream.Stream;

/**
 * Tensor type - ordered value or values.
 * 
 * @param <T> the type of element(s)
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface Tensor<P extends Primitive<T>, T> {

    int rank();

    int[] shape();

    T get(int... idx);

    /**
     * Get the primitive value with the type T.
     *
     * @return the primitive value
     */
    P getPrimitive();

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