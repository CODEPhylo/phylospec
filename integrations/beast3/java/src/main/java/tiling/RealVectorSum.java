package tiling;

import beast.base.core.Input;
import beast.base.core.Loggable;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.Real;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import java.io.PrintStream;

/** A live, read-only sum of a real vector. */
public final class RealVectorSum extends CalculationNode implements RealScalar<Real>, Loggable {

    public final Input<RealVector<? extends Real>> vectorInput =
            new Input<>("vector", "Vector whose current elements are summed.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        if (vectorInput.get() == null) {
            throw new IllegalArgumentException("A real vector is required.");
        }
    }

    @Override
    public double get() {
        // Logger-only nodes are outside State's posterior dependency traversal.
        RealVector<? extends Real> vector = vectorInput.get();
        double sum = 0.0;
        for (int i = 0; i < vector.size(); i++) {
            sum += vector.get(i);
        }
        return sum;
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
