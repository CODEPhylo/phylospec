package tiling;

import beast.base.core.Input;
import beast.base.core.Loggable;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.type.RealScalar;
import java.io.PrintStream;

/** A live, read-only square root of a non-negative scalar. */
public final class SqrtValue extends CalculationNode implements RealScalar<NonNegativeReal>, Loggable {

    public final Input<RealScalar<? extends NonNegativeReal>> xInput =
            new Input<>("x", "Scalar whose current square root is returned.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        if (xInput.get() == null) {
            throw new IllegalArgumentException("A non-negative scalar is required.");
        }
    }

    @Override
    public double get() {
        // Read the current state, including a restored state after a rejected proposal.
        return Math.sqrt(xInput.get().get());
    }

    @Override
    public NonNegativeReal getDomain() {
        return NonNegativeReal.INSTANCE;
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
