package tiling;

import beast.base.core.Input;
import beast.base.evolution.tree.TreeInterface;
import beast.base.spec.domain.Real;
import beast.base.spec.evolution.speciation.YuleModel;
import beast.base.spec.type.RealScalar;

/** Native Yule density with dependency tracking for derived scalar parameters. */
public final class LiveYuleModel extends YuleModel {

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
        // Native Yule checks direct StateNodes only. BEAST invokes this method on affected
        // dependency paths, including paths through derived CalculationNode parameters.
        return true;
    }
}
