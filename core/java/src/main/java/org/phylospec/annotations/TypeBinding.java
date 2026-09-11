package org.phylospec.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds one package-defined PhyloSpec semantic type to its Java representation.
 *
 * <p>The semantic type must be declared in a component library loaded by the
 * annotation processor. This annotation may be placed directly on a public
 * implementation class, or on a separate mapping interface when the package
 * implementation cannot be annotated.</p>
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface TypeBinding {

    /** Fully-qualified, non-generic type name from a PhyloSpec component library. */
    String semantic();

    /** Java representation, inferred when annotating the implementation class. */
    Class<?> implementation() default Void.class;
}
