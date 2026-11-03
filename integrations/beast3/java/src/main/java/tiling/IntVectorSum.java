package tiling;

import beast.base.core.Input;
import beast.base.core.Loggable;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.Int;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.IntVector;
import java.io.PrintStream;

/** A live, read-only sum that rejects results outside the signed 32-bit integer range. */
public final class IntVectorSum extends CalculationNode implements IntScalar<Int>, Loggable {

    public final Input<IntVector<? extends Int>> vectorInput =
            new Input<>("vector", "Vector whose current elements are summed.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        if (vectorInput.get() == null) {
            throw new IllegalArgumentException("An integer vector is required.");
        }
    }

    @Override
    public int get() {
        // Logger-only nodes are outside State's posterior dependency traversal.
        IntVector<? extends Int> vector = vectorInput.get();
        // The final result may fit even when an intermediate sum exceeds the int range.
        long sum = 0;
        for (int i = 0; i < vector.size(); i++) {
            sum += vector.get(i);
        }
        try {
            return Math.toIntExact(sum);
        } catch (ArithmeticException overflow) {
            throw new ArithmeticException("Integer vector sum " + sum + " is outside the signed 32-bit integer range.");
        }
    }

    @Override
    public Int getDomain() {
        return Int.INSTANCE;
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
