package org.phylospec.types.impl;

import org.phylospec.types.PhyloSpecType;
import org.phylospec.types.SquareMatrix;
import java.util.List;

/**
 * Immutable implementation of the SquareMatrix type.
 * 
 * @param <T> the type of elements in this matrix
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public class SquareMatrixImpl<T extends PhyloSpecType> 
        extends MatrixImpl<T> implements SquareMatrix<T> {
    
    /**
     * Constructs a SquareMatrix from a 2D list of elements.
     * 
     * @param elements the matrix elements (must be square)
     * @throws IllegalArgumentException if the matrix is not square
     */
    public SquareMatrixImpl(List<List<T>> elements) {
        super(elements);
        if (!isValid()) {
            throw new IllegalArgumentException(
                String.format("Square matrix must have equal rows and columns, but was %dx%d", 
                             getRows(), getCols()));
        }
    }
    
    @Override
    public String getTypeName() {
        return "SquareMatrix";
    }
}
