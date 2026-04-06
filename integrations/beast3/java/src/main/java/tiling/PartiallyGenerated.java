package tiling;

import java.util.function.BiFunction;

public class PartiallyGenerated<T, M> {
    private final T partiallyGeneratedObject;
    private final BiFunction<T, M, T> completeFunc;

    public PartiallyGenerated(T partiallyGeneratedObject, BiFunction<T, M, T> completeFunc) {
        this.partiallyGeneratedObject = partiallyGeneratedObject;
        this.completeFunc = completeFunc;
    }

    public T complete(M missingPart) {
        return this.completeFunc.apply(this.partiallyGeneratedObject, missingPart);
    }
}
