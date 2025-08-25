package org.phylospec.types;

@Deprecated
public interface Scalar extends Tensor {

    @Override
    default int rank(){ return 0; }

    @Override
    default int[] shape(){ return new int[]{}; }

    // rank() == 0 but size == 1
}