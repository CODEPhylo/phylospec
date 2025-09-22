package org.phylospec.types;

import org.phylospec.Bounded;
import org.phylospec.domain.Real;

public interface RealScalar<D extends Real> extends Scalar<D, Double>, Bounded<Double> {

    /**
     * Overload {@link Tensor#get(int...)}
     *
     * @return unboxed value
     */
    double get();

    @Override
    default Double getLower() {
        D domain = domainType();
        return domain.getLower();
    }

    @Override
    default Double getUpper() {
        D domain = domainType();
        return domain.getUpper();
    }

    @Override
    default boolean lowerInclusive() {
        return true;
    }

    @Override
    default boolean upperInclusive() {
        return true;
    }
}