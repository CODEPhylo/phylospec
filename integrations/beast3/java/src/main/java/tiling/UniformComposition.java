package tiling;

import beast.base.core.BEASTInterface;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Input.Validate;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.type.IntSimplex;
import java.util.List;
import java.util.Random;

/**
 * Uniform prior over the compositions of a fixed total into positive integer parts.
 *
 * <p>There are {@code C(total - 1, k - 1)} such compositions for {@code k} parts, so every valid
 * state has log density {@code -log C(total - 1, k - 1)}. States with a part below one or with the
 * wrong sum have zero density. The explicit check matters because {@code IntSimplex.isValid()}
 * does not enforce the element domain.
 */
@Description("Uniform prior over compositions of a fixed total into positive integer parts")
public class UniformComposition extends Distribution {

    public final Input<IntSimplex<? extends PositiveInt>> parameterInput =
            new Input<>("parameter", "the composition (positive integers summing to the total)", Validate.REQUIRED);

    @Override
    public void initAndValidate() {}

    @Override
    public double calculateLogP() {
        IntSimplex<? extends PositiveInt> composition = this.parameterInput.get();
        int parts = composition.size();
        int total = composition.expectedSum();

        int sum = 0;
        for (int i = 0; i < parts; i++) {
            int value = composition.get(i);
            if (value < 1) {
                this.logP = Double.NEGATIVE_INFINITY;
                return this.logP;
            }
            sum += value;
        }
        if (sum != total) {
            this.logP = Double.NEGATIVE_INFINITY;
            return this.logP;
        }

        this.logP = -logBinomial(total - 1, parts - 1);
        return this.logP;
    }

    /** log of n choose k, exact for the small values used here. */
    static double logBinomial(int n, int k) {
        double result = 0.0;
        for (int i = 1; i <= k; i++) {
            result += Math.log(n - k + i) - Math.log(i);
        }
        return result;
    }

    @Override
    public List<String> getArguments() {
        return List.of(((BEASTInterface) this.parameterInput.get()).getID());
    }

    @Override
    public List<String> getConditions() {
        return List.of();
    }

    @Override
    public void sample(State state, Random random) {
        throw new UnsupportedOperationException("Sampling a uniform composition is not supported.");
    }
}
