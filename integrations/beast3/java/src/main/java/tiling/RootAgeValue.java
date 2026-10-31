package tiling;

import beast.base.core.Input;
import beast.base.core.Loggable;
import beast.base.evolution.tree.Tree;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.type.RealScalar;
import java.io.PrintStream;

/** A live, read-only view of the current root height of a tree. */
public final class RootAgeValue extends CalculationNode implements RealScalar<NonNegativeReal>, Loggable {

    public final Input<Tree> treeInput =
            new Input<>("tree", "Tree whose current root age is returned.", Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() {
        if (treeInput.get() == null) {
            throw new IllegalArgumentException("A tree is required.");
        }
    }

    @Override
    public double get() {
        return treeInput.get().getRoot().getHeight();
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
