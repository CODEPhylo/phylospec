package org.phylospec.types;

import org.phylospec.primitives.Primitive;

public interface NumberVector<P extends Number> extends NumberTensor {

    @Override
    default int rank(){ return 1; }

    @Override
    default int[] shape(){
        return new int[]{Math.toIntExact(size())};
    }

    @Override
    default boolean isValid() {
        Primitive p = primitiveType();
        for (int i=0; i<Math.toIntExact(size()); i++)
            if (!p.isValid(get(i)))
                return false;
        return true;
    }

}
