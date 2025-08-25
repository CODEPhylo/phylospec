package org.phylospec.types;

import org.phylospec.primitives.Int;

public interface IntScalar<P extends Int> extends Tensor<P, Integer> {

    @Override
    default int rank(){ return 0; }

    @Override
    default int[] shape(){ return new int[]{}; }

    default int getInt() {
        return get(0);
    }

    @Override
    default boolean isValid() {
        P p = getPrimitive();
        return p.isValid(get());
    }
}