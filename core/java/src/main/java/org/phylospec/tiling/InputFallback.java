package org.phylospec.tiling;

/**
 * Supplies an engine input when an optional PhyloSpec argument is absent.
 *
 * @param <T> engine input value type
 * @param <S> engine state type
 */
@FunctionalInterface
public interface InputFallback<T, S> {

    /** Creates the value used for the missing argument. */
    T get(S state);
}
