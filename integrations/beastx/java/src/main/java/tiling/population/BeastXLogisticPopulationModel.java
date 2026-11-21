package tiling.population;

import dr.evolution.coalescent.DemographicFunction;
import dr.evolution.util.Units;
import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import dr.inference.model.Parameter;

/**
 * PhyloSpec's {@code logisticPopulationFunction}: the population size at age t is
 * {@code N(t) = K / (1 + exp(r * (t - t50)))}, with carrying capacity K, growth rate r and
 * inflection age t50, where {@code N(t50) = K / 2}.
 *
 * <p>BEAST X's LogisticGrowthModel takes the present-day size and the age at which the size is
 * half of it, which describes a different curve. The parameters are read each time, so the
 * function follows them when they are sampled.</p>
 */
public class BeastXLogisticPopulationModel extends DemographicModel {

    private final Parameter carryingCapacity;
    private final Parameter growthRate;
    private final Parameter inflectionAge;

    public BeastXLogisticPopulationModel(
            String id,
            Parameter carryingCapacity,
            Parameter growthRate,
            Parameter inflectionAge
    ) {
        super(id);
        this.carryingCapacity = carryingCapacity;
        this.growthRate = growthRate;
        this.inflectionAge = inflectionAge;

        addVariable(carryingCapacity);
        addVariable(growthRate);
        addVariable(inflectionAge);
        setUnits(Units.Type.YEARS);
    }

    @Override
    public DemographicFunction getDemographicFunction() {
        return new LogisticFunction(
                carryingCapacity.getParameterValue(0),
                growthRate.getParameterValue(0),
                inflectionAge.getParameterValue(0),
                getUnits()
        );
    }

    private static class LogisticFunction extends DemographicFunction.Abstract {

        private final double carryingCapacity;
        private final double growthRate;
        private final double inflectionAge;

        private LogisticFunction(double carryingCapacity, double growthRate, double inflectionAge, Units.Type units) {
            super(units);
            this.carryingCapacity = carryingCapacity;
            this.growthRate = growthRate;
            this.inflectionAge = inflectionAge;
        }

        @Override
        public double getDemographic(double t) {
            return carryingCapacity / (1.0 + Math.exp(growthRate * (t - inflectionAge)));
        }

        /** Integral of 1 / N(s) from 0 to t: (t + (exp(r (t - t50)) - exp(-r t50)) / r) / K. */
        @Override
        public double getIntensity(double t) {
            if (growthRate == 0.0) {
                return 2.0 * t / carryingCapacity;
            }

            double growth = Math.exp(growthRate * (t - inflectionAge)) - Math.exp(-growthRate * inflectionAge);
            return (t + growth / growthRate) / carryingCapacity;
        }

        /** The intensity increases with t, so its inverse is found by bisection. */
        @Override
        public double getInverseIntensity(double x) {
            double low = 0.0;
            double high = 1.0;
            while (getIntensity(high) < x) {
                high *= 2.0;
            }
            for (int i = 0; i < 200; i++) {
                double middle = 0.5 * (low + high);
                if (getIntensity(middle) < x) {
                    low = middle;
                } else {
                    high = middle;
                }
            }
            return 0.5 * (low + high);
        }

        @Override
        public int getNumArguments() {
            return 0;
        }

        @Override
        public String getArgumentName(int n) {
            throw new IndexOutOfBoundsException("logisticPopulationFunction has no direct arguments.");
        }

        @Override
        public double getArgument(int n) {
            throw new IndexOutOfBoundsException("logisticPopulationFunction has no direct arguments.");
        }

        @Override
        public void setArgument(int n, double value) {
            throw new IndexOutOfBoundsException("logisticPopulationFunction has no direct arguments.");
        }

        @Override
        public double getLowerBound(int n) {
            throw new IndexOutOfBoundsException("logisticPopulationFunction has no direct arguments.");
        }

        @Override
        public double getUpperBound(int n) {
            throw new IndexOutOfBoundsException("logisticPopulationFunction has no direct arguments.");
        }
    }
}
