package org.phylospec.types;

import org.phylospec.primitives.Real;

public interface RealScalar<P extends Real> extends Scalar<P, Double> {

    /**
     * Overload {@link Tensor#get(int...)}
     *
     * @return unboxed value
     */
    double get();

}