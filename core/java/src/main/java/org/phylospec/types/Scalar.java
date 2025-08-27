package org.phylospec.types;

import org.phylospec.primitives.Real;

public interface Scalar<P extends Real> extends NumberScalar {

    /**
     * Overload {@link Tensor#get(int...)}
     *
     * @return unboxed value
     */
    double get();

}