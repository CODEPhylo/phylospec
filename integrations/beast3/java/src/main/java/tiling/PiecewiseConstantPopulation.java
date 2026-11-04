package tiling;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.Real;
import beast.base.spec.type.RealVector;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** A time-based skyline with live population sizes and change times, of fixed dimension. */
public final class PiecewiseConstantPopulation extends PopulationFunction.Abstract {

    public final Input<RealVector<? extends Real>> populationSizesInput =
            new Input<>("populationSizes", "One positive population size for each epoch.", Input.Validate.REQUIRED);
    public final Input<RealVector<? extends Real>> changeTimesInput = new Input<>(
            "changeTimes", "Increasing times before the present, in tree-height units.", Input.Validate.REQUIRED);

    private int populationCount;

    @Override
    public void initAndValidate() {
        RealVector<? extends Real> sizes = populationSizesInput.get();
        if (sizes == null || sizes.size() == 0 || changeTimesInput.get() == null) {
            throw new IllegalArgumentException("SkylineCoalescent requires population sizes and change times.");
        }
        populationCount = sizes.size();
        if (changeTimesInput.get().size() != populationCount - 1) {
            throw new IllegalArgumentException(
                    "SkylineCoalescent requires one fewer changeTimes than populationSizes.");
        }
        if (!hasValidChangeTimes()) {
            throw new IllegalArgumentException(
                    "SkylineCoalescent changeTimes must be finite, positive and strictly increasing.");
        }
        if (!hasValidPopulationSizes()) {
            throw new IllegalArgumentException("SkylineCoalescent populationSizes must be finite and positive.");
        }
    }

    /** Invalid population proposals have zero density; changing dimension is unsupported. */
    public boolean hasValidPopulationSizes() {
        RealVector<? extends Real> sizes = populationSizesInput.get();
        if (sizes.size() != populationCount) {
            throw new IllegalArgumentException(
                    "SkylineCoalescent populationSizes must retain their initial dimension.");
        }
        for (int i = 0; i < sizes.size(); i++) {
            if (!Double.isFinite(sizes.get(i)) || sizes.get(i) <= 0.0) return false;
        }
        return true;
    }

    /** The epoch labels retain their order; invalid boundary proposals are rejected, not sorted. */
    public boolean hasValidChangeTimes() {
        RealVector<? extends Real> times = changeTimesInput.get();
        if (times.size() != populationCount - 1) {
            throw new IllegalArgumentException("SkylineCoalescent changeTimes must retain their initial dimension.");
        }
        double previous = 0.0;
        for (int i = 0; i < times.size(); i++) {
            double time = times.get(i);
            if (!Double.isFinite(time) || time <= previous) return false;
            previous = time;
        }
        return true;
    }

    @Override
    public List<String> getParameterIds() {
        return Stream.of(populationSizesInput.get(), changeTimesInput.get())
                .flatMap(vector -> parameterIds(vector).stream())
                .distinct()
                .toList();
    }

    private static List<String> parameterIds(RealVector<? extends Real> vector) {
        if (vector instanceof RealVectorFromScalars values) {
            return values.valuesInput.get().stream()
                    .filter(BEASTInterface.class::isInstance)
                    .map(BEASTInterface.class::cast)
                    .map(BEASTInterface::getID)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (vector instanceof BEASTInterface value && value.getID() != null) {
            return List.of(value.getID());
        }
        return List.of();
    }

    private int epoch(double time) {
        RealVector<? extends Real> times = changeTimesInput.get();
        int lower = 0;
        int upper = times.size();
        while (lower < upper) {
            int middle = lower + (upper - lower) / 2;
            // At a change time, use the next (older) epoch.
            if (time >= times.get(middle)) lower = middle + 1;
            else upper = middle;
        }
        return lower;
    }

    @Override
    public double getPopSize(double time) {
        return populationSizesInput.get().get(epoch(time));
    }

    @Override
    public double getIntensity(double time) {
        return getIntegral(0.0, time);
    }

    @Override
    public double getIntegral(double start, double finish) {
        if (start < 0.0 || finish < start || Double.isNaN(start) || Double.isNaN(finish)) {
            throw new IllegalArgumentException("Skyline integration requires 0 <= start <= finish.");
        }
        if (start == finish) return 0.0;
        RealVector<? extends Real> times = changeTimesInput.get();
        double integral = 0.0;
        int index = epoch(start);
        while (index < times.size() && times.get(index) < finish) {
            integral += (times.get(index) - start) / populationSizesInput.get().get(index);
            start = times.get(index++);
        }
        return integral + (finish - start) / populationSizesInput.get().get(index);
    }

    @Override
    public double getInverseIntensity(double intensity) {
        if (intensity < 0.0 || Double.isNaN(intensity)) {
            throw new IllegalArgumentException("Skyline intensity must be nonnegative.");
        }
        RealVector<? extends Real> times = changeTimesInput.get();
        double start = 0.0;
        for (int index = 0; index < times.size(); index++) {
            double size = populationSizesInput.get().get(index);
            double area = (times.get(index) - start) / size;
            if (intensity <= area) return start + intensity * size;
            intensity -= area;
            start = times.get(index);
        }
        return start + intensity * populationSizesInput.get().get(times.size());
    }
}
