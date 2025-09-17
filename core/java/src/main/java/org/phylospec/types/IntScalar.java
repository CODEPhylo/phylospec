package org.phylospec.types;

import org.phylospec.primitives.Int;

public interface IntScalar<P extends Int> extends Scalar<P, Integer> {

    /**
     * Overload {@link Tensor#get(int...)}
     *
     * @return unboxed value
     */
    int get();

}