package org.phylospec.types;

import org.phylospec.domain.Domain;

import java.util.List;

public interface Vector<D extends Domain<T>, T> extends Tensor<D, T> {

    /**
     * Get all elements in the vector.
     *
     * @return an unmodifiable list of all elements
     */
    List<D> getElements();

    @Override
    default int rank(){ return 1; }

    @Override
    default int[] shape(){
        return new int[]{Math.toIntExact(size())};
    }

    @Override
    default boolean isValid() {
        D d = domainType();
        for (int i=0; i<Math.toIntExact(size()); i++)
            if (!d.isValid(get(i)))
                return false;
        return true;
    }

}
