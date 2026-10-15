package tiles.distributions;

import beast.base.core.Input;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.inference.parameter.IntSimplexParam;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/** Normalized uniform law on ordered positive integer compositions of a fixed total. */
public final class UniformComposition extends Distribution {
    public final Input<IntSimplexParam<PositiveInt>> parameterInput =
            new Input<>("parameter", "Positive integer composition", Input.Validate.REQUIRED);
    private double logMass;

    @Override
    public void initAndValidate() {
        var parameter = parameterInput.get();
        int total = parameter.expectedSum();
        int size = parameter.size();
        if (size < 1 || total < size) {
            throw new IllegalArgumentException("UniformComposition requires 1 <= num <= total.");
        }
        // There are choose(total - 1, size - 1) ordered positive compositions.
        // Use the shorter log-product to avoid overflow in factorials/binomial coefficients.
        int k = Math.min(size - 1, total - size);
        logMass = 0.0;
        for (int i = 1; i <= k; i++) {
            logMass -= Math.log((double) (total - i) / i);
        }
    }

    @Override
    public double calculateLogP() {
        var parameter = parameterInput.get();
        long sum = 0;
        for (int i = 0; i < parameter.size(); i++) {
            int value = parameter.get(i);
            if (value < 1) return logP = Double.NEGATIVE_INFINITY;
            sum += value;
        }
        return logP = sum == parameter.expectedSum() ? logMass : Double.NEGATIVE_INFINITY;
    }

    @Override
    public List<String> getArguments() {
        return List.of(parameterInput.get().getID());
    }

    @Override
    public List<String> getConditions() {
        return List.of();
    }

    @Override
    public void sample(State state, Random random) {
        if (sampledFlag) return;
        sampledFlag = true;
        var parameter = parameterInput.get();
        int total = parameter.expectedSum();
        // Uniform subset of size num - 1 from the total - 1 possible cut positions.
        var cuts = new HashSet<Integer>();
        for (int j = total - parameter.size() + 1; j < total; j++) {
            int cut = random.nextInt(j) + 1;
            if (!cuts.add(cut)) cuts.add(j);
        }
        int[] sorted = cuts.stream().mapToInt(Integer::intValue).sorted().toArray();
        int previous = 0;
        for (int i = 0; i < sorted.length; i++) {
            parameter.set(i, sorted[i] - previous);
            previous = sorted[i];
        }
        parameter.set(parameter.size() - 1, total - previous);
    }
}
