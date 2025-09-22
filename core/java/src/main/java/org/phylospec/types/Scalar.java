package org.phylospec.types;

import org.phylospec.domain.Domain;

public interface Scalar<D extends Domain<T>, T> extends Tensor<D, T> {
    // rank() == 0 but size == 1

    @Override
    default int rank(){ return 0; }

    @Override
    default int[] shape(){ return new int[]{}; }

    @Override
    default boolean isValid() {
        D d = domainType();
        return d.isValid(get());
    }

}