package tiling;

import beast.base.core.Input;
import beast.base.core.Loggable;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeUtils;
import beast.base.inference.CalculationNode;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.type.RealScalar;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A live, read-only MRCA age for a fixed set of exact tree-tip identifiers. */
public final class MrcaAgeValue extends CalculationNode implements RealScalar<NonNegativeReal>, Loggable {

    public final Input<Tree> treeInput =
            new Input<>("tree", "Tree whose current MRCA age is returned.", Input.Validate.REQUIRED);
    public final Input<List<String>> cladeInput = new Input<>(
            "clade",
            "Fixed exact tip identifiers; repeated names select the same tip.",
            new ArrayList<>(),
            Input.Validate.REQUIRED);

    private Set<String> selectedNames;

    @Override
    public void initAndValidate() {
        Tree tree = treeInput.get();
        if (tree == null) {
            throw new IllegalArgumentException("A tree is required.");
        }
        List<String> names = cladeInput.get();
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("Select at least one taxon for mrca.");
        }
        Set<String> selection = new LinkedHashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("MRCA taxon names must not be blank.");
            }
            selection.add(name);
        }

        Set<String> found = new HashSet<>();
        for (Node leaf : tree.getExternalNodes()) {
            String name = leaf.getID();
            if (selection.contains(name) && !found.add(name)) {
                throw new IllegalArgumentException("Taxon '" + name + "' matches more than one tree tip.");
            }
        }
        for (String name : selection) {
            if (!found.contains(name)) {
                throw new IllegalArgumentException("Taxon '" + name
                        + "' is not a tree tip. Use exact tip identifiers; species aliases are not supported.");
            }
        }
        selectedNames = Set.copyOf(selection);
    }

    @Override
    public double get() {
        // Restoration can replace nodes, and topology proposals can change the MRCA.
        // Resolve it on the current tree rather than retaining an ancestor node.
        Node ancestor = TreeUtils.getCommonAncestorNode(treeInput.get(), selectedNames);
        if (ancestor == null) {
            throw new IllegalArgumentException("The current tree no longer contains all selected MRCA taxa.");
        }
        return ancestor.getHeight();
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
