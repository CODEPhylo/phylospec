package tiling.model;

import java.util.function.BiConsumer;

/**
 * Delays binding an observed value to a BEAST X likelihood during tiling until the observed value is available.
 */
public class UnboundDistribution<T> {

    private final BiConsumer<T, String> bindFunc;

    public UnboundDistribution(BiConsumer<T, String> bindFunc) {
        this.bindFunc = bindFunc;
    }

    public void bind(T observedValue, String id) {
        this.bindFunc.accept(observedValue, id);
    }

}
