package tiles.observations;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.evolution.tree.MRCAPrior;
import beast.base.spec.inference.distribution.Uniform;
import beast.base.spec.inference.parameter.RealScalarParam;
import tiles.MultiAstNodeTile;
import tiling.BEASTState;

public class RootObservedBetweenTile extends MultiAstNodeTile<RealScalarParam<PositiveReal>> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x = rootAge(tree=$tree) observed between [$from, $to]";
    }

    TileInput<Tree> treeInput = new TileInput<>("$tree");
    TileInput<RealScalarParam<Real>> fromInput = new TileInput<>("$from");
    TileInput<RealScalarParam<Real>> toInput = new TileInput<>("$to");

    @Override
    public RealScalarParam<PositiveReal> applyTile(BEASTState beastState) {
        Tree tree = this.treeInput.apply(beastState);
        RealScalarParam<Real> from = this.fromInput.apply(beastState);
        RealScalarParam<Real> to = this.toInput.apply(beastState);

        // we create a uniform distribution as our prior

        Uniform uniform = new Uniform();
        beastState.setInput(uniform, uniform.lowerInput, from);
        beastState.setInput(uniform, uniform.upperInput, to);

        // we create a new MRCAPrior

        MRCAPrior prior = new MRCAPrior();
        beastState.setInput(prior, prior.distInput, uniform);
        beastState.setInput(prior, prior.treeInput, tree);
        beastState.setInput(prior, prior.taxonsetInput, tree.getTaxonset());

        // we add the prior as likelihood to the beast state

        beastState.addDistribution(from, prior, "rootCalibration");

        // we return the observed root age

        return new RealScalarParam<>(tree.getRoot().getHeight(), PositiveReal.INSTANCE);
    }

}
