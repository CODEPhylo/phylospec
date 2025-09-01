package org.phylospec.types;

import org.phylospec.primitives.Primitive;

import java.util.List;

public interface NumberVector<P extends Primitive<T>, T extends Number> extends Tensor<P, T> {

    /**
     * Get all elements in the vector.
     *
     * @return an unmodifiable list of all elements
     */
    List<P> getElements();

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
