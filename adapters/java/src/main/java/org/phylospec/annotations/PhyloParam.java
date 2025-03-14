package org.phylospec.annotations;

import java.lang.annotation.*;

/**
 * Marks a field, method, or parameter as corresponding to a PhyloSpec parameter.
 * 
 * This annotation provides information about how a parameter in an implementation
 * corresponds to a parameter defined in the PhyloSpec specification, allowing for
 * automated mapping between different model representations.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface PhyloParam {
    /**
     * The name of the parameter as defined in the PhyloSpec specification.
     */
    String value();
    
    /**
     * Whether this parameter is required according to the PhyloSpec specification.
     */
    boolean required() default true;
    
    /**
     * The default value to use if not provided (as a string representation).
     * Empty string indicates no default value.
     */
    String defaultValue() default "";
}