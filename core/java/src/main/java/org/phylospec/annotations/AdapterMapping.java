package org.phylospec.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes a {@code TypeAdapter} available for automatic selection while compiling the module that
 * declares it.
 *
 * <p>Adapters supplied by another module are selected explicitly with {@link
 * InputMapping#adapter()} so that generated mappings never depend on ambiguous classpath scanning.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AdapterMapping {}
