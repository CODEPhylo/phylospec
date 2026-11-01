package tiling;

import beast.base.core.Input;
import beast.base.core.Loggable;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.type.RealScalar;
import java.io.PrintStream;

/** A live, read-only natural logarithm of a positive real scalar. */
public final class LogValue extends CalculationNode implements RealScalar<Real>, Loggable {

    public final Input<RealScalar<? extends PositiveReal>> xInput =
            new Input<>("x", "Positive scalar whose current natural logarithm is returned.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        if (xInput.get() == null) {
            throw new IllegalArgumentException("A positive real scalar is required.");
        }
    }

    @Override
    public double get() {
        // Read the current state, including a restored state after a rejected proposal.
        return Math.log(xInput.get().get());
    }

    @Override
    public Real getDomain() {
        return Real.INSTANCE;
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
