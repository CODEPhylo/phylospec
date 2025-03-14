package org.phylospec.annotations;

import java.lang.annotation.*;

/**
 * Marks a class as implementing a PhyloSpec-compatible component.
 * This annotation identifies the component type and provides information about how
 * it maps to PhyloSpec concepts.
 * 
 * This allows software to discover and use PhyloSpec-compatible implementations
 * for automated model construction and translation between different modeling frameworks.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PhyloSpec {
    /**
     * The name of the PhyloSpec component (e.g., "HKY", "GTR", "Yule", "PhyloCTMC")
     * as defined in the PhyloSpec specification.
     */
    String value();
    
    /**
     * The category this component belongs to.
     */
    Category category() default Category.FUNCTION;
    
    /**
     * The semantic role this implementation plays.
     */
    Role role() default Role.OTHER;
    
    /**
     * Categories of PhyloSpec components
     */
    enum Category {
        /** Deterministic functions like substitution models */
        FUNCTION,
        
        /** Probability distributions */
        DISTRIBUTION,
        
        /** Prior distributions */
        PRIOR,
        
        /** Operators for MCMC sampling */
        OPERATOR,
        
        /** Data structures */
        DATA_STRUCTURE
    }
    
    /**
     * Semantic roles that implementations can play
     */
    enum Role {
        /** Substitution model (HKY, GTR, etc.) */
        SUBSTITUTION_MODEL,
        
        /** Tree prior (Yule, Birth-Death, etc.) */
        TREE_PRIOR,
        
        /** Clock model */
        CLOCK_MODEL,
        
        /** Site model */
        SITE_MODEL,
        
        /** Tree structure */
        TREE,
        
        /** Sequence alignment */
        ALIGNMENT,
        
        /** Parameter (RealParameter, etc.) */
        PARAMETER,
        
        /** MCMC sampler */
        MCMC,
        
        /** Likelihood component */
        LIKELIHOOD,
        
        /** Other role */
        OTHER
    }
}