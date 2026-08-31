package org.phylospec.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a mapping definition can be used to generate a Tile
 * for one fully-qualified PhyloSpec generator.
 *
 * <p>This annotation describes an engine mapping. Semantic information
 * such as arguments, required status, defaults, and constraints remains
 * in the PhyloSpec component library.</p>
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface GeneratorMapping {

    /**
     * Fully-qualified generator name from the component library.
     *
     * <p>For example:
     * {@code phylospec.functions.coalescent.constantPopulationFunction}.
     */
    String component();

    /**
     * Engine implementation class constructed by the generated Tile.
     */
    Class<?> implementation();
}
