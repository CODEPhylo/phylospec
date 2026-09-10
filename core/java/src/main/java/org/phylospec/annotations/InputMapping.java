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
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface InputMapping {

    /**
     * Argument name in the PhyloSpec component library.
     */
    String argument();

    /**
     * Java field name of the corresponding engine input.
     *
     * <p>This may be omitted when the annotation is placed directly on
     * the engine input field.</p>
     */
    String input() default "";

    /**
     * Adapter used when the argument value cannot be assigned
     * directly to the engine input.
     *
     * <p>Specify this explicitly for an adapter supplied by another module. An adapter annotated
     * with {@link AdapterMapping} in the current compilation can be selected automatically when the
     * conversion is unambiguous.
     */
    Class<?> adapter() default Void.class;
}
