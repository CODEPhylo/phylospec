package tiling;

import beast.base.core.Input;
import beast.base.evolution.tree.TreeInterface;
import beast.base.spec.domain.Real;
import beast.base.spec.evolution.speciation.BirthDeathGernhard08Model;
import beast.base.spec.type.RealScalar;

/** Native Gernhard08 density with dependency tracking for derived scalar parameters. */
public class LiveBirthDeathGernhard08Model extends BirthDeathGernhard08Model {

    public final Input<RealScalar<? extends Real>> rootAgeParameterInput =
            new Input<>("rootAge", "Age bound to the tree root, distinct from the process origin.");

    @Override
    public void validateInputs() {
        if (rootAgeParameterInput.get() == null) super.validateInputs();
        else RootAgeSupport.validateInputs(this, rootAgeParameterInput.get());
    }

    @Override
    public double calculateTreeLogLikelihood(TreeInterface tree) {
        if (rootAgeParameterInput.get() != null && !RootAgeSupport.compatibleTree(tree, rootAgeParameterInput.get())) {
            return Double.NEGATIVE_INFINITY;
        }
        return super.calculateTreeLogLikelihood(tree);
    }

    @Override
    protected boolean requiresRecalculation() {
        // The native implementation only checks parameters that are direct StateNodes.
        // BEAST already limits this callback to affected dependency paths.
        return true;
    }
}
