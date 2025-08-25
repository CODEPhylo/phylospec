package org.phylospec.types;

import org.phylospec.primitives.Int;

public interface IntTensor<P extends Int> extends Tensor<P, Integer> {

    default int getInt() {
        return get(0);
    }

}