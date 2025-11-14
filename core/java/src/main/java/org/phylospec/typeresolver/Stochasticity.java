package org.phylospec.typeresolver;

import java.util.Arrays;
import java.util.List;

public enum Stochasticity {
    CONSTANT,
    DETERMINISTIC,
    STOCHASTIC,
    UNDEFINED;

    public static Stochasticity nonConstant(Stochasticity stochasticity) {
        return stochasticity == CONSTANT ? DETERMINISTIC : stochasticity;
    }

    public static Stochasticity merge(Stochasticity... stochasticities) {
        List<Stochasticity> stochasticityList = Arrays.stream(stochasticities).toList();
        return merge(stochasticityList);
    }

    public static Stochasticity merge(List<Stochasticity> stochasticities) {
        if (stochasticities.contains(Stochasticity.UNDEFINED)) return UNDEFINED;
        if (stochasticities.contains(Stochasticity.STOCHASTIC)) return STOCHASTIC;
        if (stochasticities.contains(Stochasticity.DETERMINISTIC)) return DETERMINISTIC;
        return CONSTANT;
    }
}
