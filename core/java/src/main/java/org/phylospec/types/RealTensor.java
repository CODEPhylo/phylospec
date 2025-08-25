package org.phylospec.types;

import org.phylospec.primitives.Real;

public interface RealTensor<P extends Real> extends Tensor<P, Double> {

    default double getDouble() {
        return get(0);
    }

    @Override
    default boolean isValid() {
        P p = getPrimitive();
        return p.isValid(get());
    }

}