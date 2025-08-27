package org.phylospec.types;

import org.phylospec.primitives.Real;

/**
 * Square matrix type.
 * 
 * Represents a matrix with equal number of rows and columns.
 * Used for transition matrices, rate matrices, and correlation matrices.
 * 
 * @param <T> the type of elements in this matrix
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface SquareMatrix<P extends Real> extends Matrix<P> {

    /**
     * {@inheritDoc}
     * 
     * A SquareMatrix is valid if it is a valid matrix with equal dimensions.
     * 
     * @return true if the matrix is square and valid, false otherwise
     */
    @Override
    default boolean isValid() {
        return Matrix.super.isValid() && rows() == cols();
    }
}
