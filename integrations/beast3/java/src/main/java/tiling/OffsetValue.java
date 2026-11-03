package tiling;

import beast.base.core.Input;
import beast.base.core.Loggable;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.Real;
import beast.base.spec.type.RealScalar;
import java.io.PrintStream;

/** A live, read-only scalar plus a fixed constant. */
public final class OffsetValue extends CalculationNode implements RealScalar<Real>, Loggable {

    public final Input<RealScalar<? extends Real>> xInput =
            new Input<>("x", "Scalar to which the constant is added.", Input.Validate.REQUIRED);
    public final Input<Double> offsetInput = new Input<>("offset", "Fixed constant to add.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        if (xInput.get() == null || offsetInput.get() == null) {
            throw new IllegalArgumentException("A real scalar and a fixed offset are required.");
        }
    }

    @Override
    public double get() {
        // Read the current state, including a restored state after a rejected proposal.
        return xInput.get().get() + offsetInput.get();
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
