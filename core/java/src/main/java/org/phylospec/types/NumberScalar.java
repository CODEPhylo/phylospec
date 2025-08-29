package org.phylospec.types;

import org.phylospec.primitives.Primitive;

public interface NumberScalar<P extends Primitive<Number>> extends NumberTensor {
    // rank() == 0 but size == 1

    @Override
    default int rank(){ return 0; }

    @Override
    default int[] shape(){ return new int[]{}; }

    @Override
    default boolean isValid() {
        Primitive p = primitiveType();
        return p.isValid(get());
    }

}