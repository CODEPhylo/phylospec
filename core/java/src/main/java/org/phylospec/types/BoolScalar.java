package org.phylospec.types;

import org.phylospec.primitives.Bool;

public interface BoolScalar<P extends Bool> extends Tensor<P, Boolean> {

    @Override
    default int rank(){ return 0; }

    @Override
    default int[] shape(){ return new int[]{}; }

    /**
     * Overload {@link Tensor#get(int...)}
     *
     * @return unboxed value
     */
    boolean get();

    @Override
    default boolean isValid() {
        P p = primitiveType();
        return p.isValid(get());
    }
}