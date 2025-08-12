package org.phylospec.types;

/**
 * Simplex type - vector of probabilities that sum to 1.
 * 
 * Represents a probability distribution over a finite set of outcomes.
 * Common uses in phylogenetics:
 * - Base frequencies (DNA: 4 values, Protein: 20 values)
 * - State frequencies in discrete trait models
 * - Mixture weights in mixture models
 * - Category probabilities in rate heterogeneity models
 * 
 * @author PhyloSpec Contributors
 * @since 1.0
 */
public interface Simplex extends Vector<Probability> {
    /**
     * {@inheritDoc}
     * 
     * @return "Simplex"
     */
    @Override
    default java.lang.String getTypeName() {
        return "Simplex";
    }
    
    /**
     * {@inheritDoc}
     * 
     * A Simplex is valid if:
     * - It contains at least one element
     * - All elements are valid probabilities
     * - The sum of all elements equals 1 (within numerical tolerance)
     * 
     * @return true if this forms a valid probability distribution, false otherwise
     */
    @Override
    default boolean isValid() {
        if (!Vector.super.isValid() || size() == 0) {
            return false;
        }
        
        double sum = 0.0;
        for (Probability p : getElements()) {
            sum += p.getValue();
        }
        
        // Check if sum is approximately 1 (with small epsilon for floating point errors)
        return Math.abs(sum - 1.0) < 1e-10;
    }
}
