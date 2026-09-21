package org.phylospec.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes a {@code TypeAdapter} available for automatic selection and exports it through the
 * module's generated adapter index.
 *
 * <p>A consuming module can import that index with {@link AdapterSource}. Explicit selection with
 * {@link InputMapping#adapter()} remains available when more than one adapter matches.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AdapterMapping {}
