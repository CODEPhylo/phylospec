package tiling;

import java.util.function.BiConsumer;

public class UnboundDistribution<T> {

    private final BiConsumer<T, String> bindFunc;

    public UnboundDistribution(BiConsumer<T, String> bindFunc) {
        this.bindFunc = bindFunc;
    }

    public void bind(T observedValue, String id) {
        this.bindFunc.accept(observedValue, id);
    }

    public void bind(T observedValue) {
        this.bind(observedValue, "likelihood");
    }
}