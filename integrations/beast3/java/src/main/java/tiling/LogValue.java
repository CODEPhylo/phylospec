package tiling;

import beast.base.core.Input;
import beast.base.core.Loggable;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.type.RealScalar;
import java.io.PrintStream;

/** A live, read-only logarithm of a positive real scalar, with an optional fixed integer base. */
public final class LogValue extends CalculationNode implements RealScalar<Real>, Loggable {

    public final Input<RealScalar<? extends PositiveReal>> xInput =
            new Input<>("x", "Positive scalar whose current logarithm is returned.", Input.Validate.REQUIRED);
    public final Input<Integer> baseInput =
            new Input<>("base", "Optional fixed integer base greater than 1; omitted for the natural logarithm.");

    @Override
    public void initAndValidate() {
        if (xInput.get() == null) {
            throw new IllegalArgumentException("A positive real scalar is required.");
        }
        if (baseInput.get() != null && baseInput.get() <= 1) {
            throw new IllegalArgumentException("Logarithm base must be an integer greater than 1.");
        }
    }

    @Override
    public double get() {
        // Read the current state, including a restored state after a rejected proposal.
        double log = Math.log(xInput.get().get());
        Integer base = baseInput.get();
        return base == null ? log : log / Math.log(base);
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
