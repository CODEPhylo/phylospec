package org.phylospec.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps one PhyloSpec generator argument to one input on the
 * engine implementation class.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface InputMapping {

    /**
     * Argument name in the PhyloSpec component library.
     */
    String argument();

    /**
     * Java field name of the corresponding engine input.
     */
    String input();
}
