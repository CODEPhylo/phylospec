package org.phylospec.tiling;

/**
 * Converts one engine-facing value type into another.
 *
 * @param <F> source value type
 * @param <T> target value type
 * @param <S> engine state type
 */
@FunctionalInterface
public interface TypeAdapter<F, T, S> {

    /**
     * Converts a source value using the current engine state.
     */
    T adapt(F value, S state);
}
