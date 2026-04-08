package tiling;

import java.util.function.Function;
import java.util.function.BiFunction;

public class Partial<T, M> {
    protected final T partiallyGeneratedObject;
    protected final BiFunction<T, M, T> completeFunc;

    public Partial(T partiallyGeneratedObject, BiFunction<T, M, T> completeFunc) {
        this.partiallyGeneratedObject = partiallyGeneratedObject;
        this.completeFunc = completeFunc;
    }

    public T complete(M missingPart) {
        return this.completeFunc.apply(this.partiallyGeneratedObject, missingPart);
    }

    public static class WithDefault<T, M> extends Partial<T, M> {

        protected final Function<T, T> defaultCompleteFunc;

        public WithDefault(T partiallyGeneratedObject, Function<T, T> defaultCompleteFunc, BiFunction<T, M, T> completeFunc) {
            super(partiallyGeneratedObject, completeFunc);
            this.defaultCompleteFunc = defaultCompleteFunc;
        }

        public T complete() {
            return this.defaultCompleteFunc.apply(this.partiallyGeneratedObject);
        }

    }
}
