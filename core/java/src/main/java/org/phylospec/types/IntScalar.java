package org.phylospec.types;

import org.phylospec.primitives.Int;
import org.phylospec.primitives.Primitive;

public interface IntScalar<P extends Int> extends NumberScalar<P, Integer> {

    /**
     * Overload {@link Tensor#get(int...)}
     *
     * @return unboxed value
     */
    int get();

}