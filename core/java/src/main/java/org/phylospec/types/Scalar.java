package org.phylospec.types;

import org.phylospec.primitives.Primitive;

public interface Scalar<P extends Primitive<T>, T> extends Tensor<P, T> {
    // rank() == 0 but size == 1

    @Override
    default int rank(){ return 0; }

    @Override
    default int[] shape(){ return new int[]{}; }

    @Override
    default boolean isValid() {
        P p = primitiveType();
        return p.isValid(get());
    }

}