package tiling;

import beast.base.core.Input;
import beast.base.core.Loggable;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.type.RealScalar;
import java.io.PrintStream;

/** A live, read-only exponential of a real scalar. */
public final class ExpValue extends CalculationNode implements RealScalar<PositiveReal>, Loggable {

    public final Input<RealScalar<? extends Real>> xInput =
            new Input<>("x", "Scalar whose current exponential is returned.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        if (xInput.get() == null) {
            throw new IllegalArgumentException("A real scalar is required.");
        }
    }

    @Override
    public double get() {
        // Read the current state, including a restored state after a rejected proposal.
        return Math.exp(xInput.get().get());
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
    public void close(PrintStream out) {
        // This value does not own any resources.
    }
}
