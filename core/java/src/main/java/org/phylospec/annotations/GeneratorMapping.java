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

    /**
     * Argument names selecting one component overload.
     *
     * <p>This can be omitted when the component has only one definition.
     * When several generators share the same qualified name, the names
     * must match one definition in component-library order.</p>
     */
    String[] arguments() default {};

    /**
     * Public type returned by the generated Tile.
     *
     * <p>By default, the implementation type is returned directly.</p>
     */
    Class<?> output() default Void.class;
}
