package tiling.rpn;

import dr.inference.model.Statistic;
import dr.inference.model.SumStatistic;

/**
 * The sum of the elements of a vector. BEAST X does not expose the statistics a SumStatistic adds,
 * so the vector is kept here for XML export.
 */
public class BeastXSumStatistic extends SumStatistic {

    private final Statistic vector;

    public BeastXSumStatistic(String name, Statistic vector) {
        // "elementwise" = true adds up the elements of the vector into one value. With false it
        // returns the vector itself, and an RPN calculation only reads its first element.
        super(name, true, new double[0]);
        addStatistic(vector);
        this.vector = vector;
    }

    public Statistic getVector() {
        return this.vector;
    }
}
