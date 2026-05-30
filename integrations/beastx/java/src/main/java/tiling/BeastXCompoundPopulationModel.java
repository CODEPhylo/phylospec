package tiling;

import dr.evolution.coalescent.DemographicFunction;
import dr.evolution.util.Units;
import dr.evomodel.coalescent.demographicmodel.DemographicModel;

import java.util.ArrayList;
import java.util.List;

public class BeastXCompoundPopulationModel extends DemographicModel {

    private final List<DemographicModel> models;
    private final double[] changeTimes;

    public BeastXCompoundPopulationModel(
            String id,
            List<DemographicModel> models,
            double[] changeTimes
    ) {
        super(id);

        if (models.size() < 2) {
            throw new IllegalArgumentException(
                    "compoundPopulationFunction requires at least two population functions."
            );
        }

        if (changeTimes.length != models.size() - 1) {
            throw new IllegalArgumentException(
                    "compoundPopulationFunction requires one fewer changeTimes value than functions."
            );
        }

        for (int i = 1; i < changeTimes.length; i++) {
            if (changeTimes[i] <= changeTimes[i - 1]) {
                throw new IllegalArgumentException(
                        "compoundPopulationFunction changeTimes must be strictly increasing."
                );
            }
        }

        this.models = new ArrayList<>(models);
        this.changeTimes = changeTimes.clone();

        for (DemographicModel model : this.models) {
            this.addModel(model);
        }

        this.setUnits(Units.Type.YEARS);
    }

    @Override
    public DemographicFunction getDemographicFunction() {
        List<DemographicFunction> functions =
                this.models.stream()
                        .map(DemographicModel::getDemographicFunction)
                        .toList();

        return new CompoundFunction(functions, this.changeTimes, this.getUnits());
    }

    private static class CompoundFunction extends DemographicFunction.Abstract {

        private final List<DemographicFunction> functions;
        private final double[] changeTimes;

        private CompoundFunction(
                List<DemographicFunction> functions,
                double[] changeTimes,
                Units.Type units
        ) {
            super(units);
            this.functions = functions;
            this.changeTimes = changeTimes.clone();
        }

        @Override
        public double getDemographic(double t) {
            return functions.get(indexForTime(t)).getDemographic(t);
        }

        @Override
        public double getIntensity(double t) {
            return getIntegral(0.0, t);
        }

        @Override
        public double getInverseIntensity(double x) {
            double low = 0.0;
            double high = 1.0;

            while (getIntensity(high) < x) {
                high *= 2.0;
            }

            for (int i = 0; i < 200; i++) {
                double mid = 0.5 * (low + high);

                if (getIntensity(mid) < x) {
                    low = mid;
                } else {
                    high = mid;
                }
            }

            return 0.5 * (low + high);
        }

        @Override
        public double getIntegral(double start, double finish) {
            if (finish < start) {
                return -getIntegral(finish, start);
            }

            double total = 0.0;
            double intervalStart = start;

            while (intervalStart < finish) {
                int index = indexForTime(intervalStart);
                double intervalFinish = finish;

                if (index < changeTimes.length) {
                    intervalFinish = Math.min(intervalFinish, changeTimes[index]);
                }

                total += functions.get(index).getIntegral(intervalStart, intervalFinish);
                intervalStart = intervalFinish;
            }

            return total;
        }

        @Override
        public int getNumArguments() {
            return 0;
        }

        @Override
        public String getArgumentName(int n) {
            throw new IndexOutOfBoundsException("compoundPopulationFunction has no direct arguments.");
        }

        @Override
        public double getArgument(int n) {
            throw new IndexOutOfBoundsException("compoundPopulationFunction has no direct arguments.");
        }

        @Override
        public void setArgument(int n, double value) {
            throw new IndexOutOfBoundsException("compoundPopulationFunction has no direct arguments.");
        }

        @Override
        public double getLowerBound(int n) {
            throw new IndexOutOfBoundsException("compoundPopulationFunction has no direct arguments.");
        }

        @Override
        public double getUpperBound(int n) {
            throw new IndexOutOfBoundsException("compoundPopulationFunction has no direct arguments.");
        }

        private int indexForTime(double t) {
            for (int i = 0; i < changeTimes.length; i++) {
                if (t < changeTimes[i]) {
                    return i;
                }
            }

            return functions.size() - 1;
        }
    }
}