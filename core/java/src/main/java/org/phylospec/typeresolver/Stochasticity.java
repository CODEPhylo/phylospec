package org.phylospec.typeresolver;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

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


    @Override
    public String toString() {
        return switch (this) {
            case CONSTANT -> "constant";
            case DETERMINISTIC -> "deterministic value";
            case STOCHASTIC -> "random variable";
            case UNDEFINED -> "undefined value";
        };
    }

    public static String getErrorMessage(String engine, String valueName, Stochasticity stochasticity, Set<Stochasticity> expectedStochasticities) {
        if (expectedStochasticities.size() == 1) {
            return engine + " expects a " + expectedStochasticities.iterator().next().toString() + " for '" + valueName + "', but you use a " + stochasticity + ".";
        }
        return "BEAST 2.8 does not support a " + stochasticity.toString() + " for '" + valueName + "'.";
    }

    public static String getErrorMessage(String engine, Stochasticity stochasticity, Set<Stochasticity> expectedStochasticities) {
        if (expectedStochasticities.size() == 1) {
            return engine + " expects a " + expectedStochasticities.iterator().next().toString() + " here, but you use a " + stochasticity + ".";
        }
        return "BEAST 2.8 does not support a " + stochasticity.toString() + " here.";
    }
}
