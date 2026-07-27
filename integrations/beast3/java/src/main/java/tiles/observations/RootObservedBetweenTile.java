package tiles.observations;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.evolution.tree.MRCAPrior;
import beast.base.spec.inference.distribution.Uniform;
import beast.base.spec.inference.parameter.RealScalarParam;
import org.phylospec.ast.Expr;
import tiles.TemplateTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;

public class RootObservedBetweenTile extends TemplateTile<RealScalarParam<PositiveReal>> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x = rootAge(tree=$tree) observed between [$from, $to]";
    }

    TemplateTileInput<Tree> treeInput = new TemplateTileInput<>("$tree");
    TemplateTileInput<RealScalarParam<Real>> fromInput = new TemplateTileInput<>("$from");
    TemplateTileInput<RealScalarParam<Real>> toInput = new TemplateTileInput<>("$to");

    @Override
    public RealScalarParam<PositiveReal> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Tree tree = this.treeInput.apply(beastState, indexVariables);
        RealScalarParam<Real> from = this.fromInput.apply(beastState, indexVariables);
        RealScalarParam<Real> to = this.toInput.apply(beastState, indexVariables);

        // we create a uniform distribution as our prior

        Uniform uniform = new Uniform();
        beastState.setInput(uniform, uniform.lowerInput, from);
        beastState.setInput(uniform, uniform.upperInput, to);

        // we create a new MRCAPrior

        MRCAPrior prior = new MRCAPrior();
        beastState.setInput(prior, prior.distInput, uniform);
        beastState.setInput(prior, prior.treeInput, tree);
        beastState.setInput(prior, prior.taxonsetInput, tree.getTaxonset());

        // A tree can have both a tree-generating prior and one or more
        // calibration priors. Register the calibration as an additional prior
        // associated with the same tree.

        beastState.addPriorDistribution(tree, prior, "rootCalibration");

        // we return the observed root age

        return new RealScalarParam<>(tree.getRoot().getHeight(), PositiveReal.INSTANCE);
    }

}
