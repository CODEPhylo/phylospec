package org.phylospec.types;

import org.phylospec.primitives.Primitive;

/**
 * Tensor type - ordered value or values.
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface NumberTensor<P extends Primitive<Number>> extends Tensor {

    // computational

    static double sum(NumberTensor<?>... tensors) {
        return 0;
    }

}