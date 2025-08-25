package org.phylospec.types;

import org.phylospec.primitives.Real;

public interface RealScalar<P extends Real> extends Tensor<P, Double> {
    // rank() == 0 but size == 1

    @Override
    default int rank(){ return 0; }

    @Override
    default int[] shape(){ return new int[]{}; }

    default double getDouble() {
        return get(0);
    }

    @Override
    default boolean isValid() {
        P p = getPrimitive();
        return p.isValid(get());
    }

}