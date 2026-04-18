package tiling;

import java.util.function.BiFunction;

/**
 * This class can be used to wrap an incomplete object. The object can be completed lazily after instantiation
 * using the given completion function.
 */
public record Partial<T, M>(T partiallyGeneratedObject, BiFunction<T, M, T> completeFunc) {

    public T complete(M missingPart) {
        return this.completeFunc.apply(this.partiallyGeneratedObject, missingPart);
    }

}
