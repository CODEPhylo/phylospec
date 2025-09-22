package org.phylospec.domain;

/**
 * Primitive types define constraints and validation.
 * The type parameter T is the Java type this primitive works with.
 */
public interface Domain<T> {

    /**
     * Check if a value is valid for this primitive type.
     */
    boolean isValid(T value);

    Class<T> getTypeClass();

}

