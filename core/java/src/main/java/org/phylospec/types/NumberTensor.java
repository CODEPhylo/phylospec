package org.phylospec.types;

import org.phylospec.domain.Domain;

/**
 * Tensor type - ordered value or values.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
@Deprecated
public interface NumberTensor<P extends Domain<Number>> extends Tensor<P, Number> {

    // computational

    static double sum(NumberTensor<?>... tensors) {
        return 0;
    }

}