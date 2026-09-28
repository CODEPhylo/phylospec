package beastobjects;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Input.Validate;
import beast.base.core.Loggable;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.type.RealScalar;
import java.io.PrintStream;

/**
 * A positive scalar multiplied by a constant factor.
 *
 * <p>This is how PhyloSpec's {@code c * x} reaches an input that needs a
 * {@code RealScalar<PositiveReal>}. The case it exists for is a partition's relative rate
 * written as {@code rate=(N / n_i) * w[i]}, with {@code w} a simplex of partition weights.
 * The result recomputes whenever {@code x} changes.
 */
@Description("A positive scalar times a constant positive factor")
public class ScaledScalar extends CalculationNode implements RealScalar<PositiveReal>, Loggable {

    public final Input<RealScalar<? extends Real>> scalarInput =
            new Input<>("scalar", "the scalar to scale", Validate.REQUIRED);
    public final Input<Double> factorInput =
            new Input<>("factor", "the constant factor, greater than zero", Validate.REQUIRED);

    private RealScalar<? extends Real> scalar;
    private double factor;

    public ScaledScalar() {}

    public ScaledScalar(RealScalar<? extends Real> scalar, double factor) {
        initByName("scalar", scalar, "factor", factor);
    }

    @Override
    public void initAndValidate() {
        scalar = scalarInput.get();
        factor = factorInput.get();
        if (!(factor > 0)) {
            throw new IllegalArgumentException("factor must be greater than zero, not " + factor);
        }
    }

    @Override
    public double get() {
        return factor * scalar.get();
    }

    @Override
    public PositiveReal getDomain() {
        return PositiveReal.INSTANCE;
    }

    @Override
    public void init(PrintStream out) {
        out.print(getID() + "\t");
    }

    @Override
    public void log(long sample, PrintStream out) {
        out.print(get() + "\t");
    }

    @Override
    public void close(PrintStream out) {}
}
