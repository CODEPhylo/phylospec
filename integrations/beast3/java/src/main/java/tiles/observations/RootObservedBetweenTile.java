package tiles.observations;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.Real;
import beast.base.spec.evolution.tree.MRCAPrior;
import beast.base.spec.inference.distribution.Uniform;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.TemplateTile;
import tiling.RootAgeValue;

public class RootObservedBetweenTile extends TemplateTile<RootAgeValue, BEASTState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x = rootAge(tree=$tree) observed between [$from, $to]";
    }

    TemplateTileInput<Tree, BEASTState> treeInput = new TemplateTileInput<>("$tree");
    TemplateTileInput<RealScalarParam<Real>, BEASTState> fromInput = new TemplateTileInput<>("$from");
    TemplateTileInput<RealScalarParam<Real>, BEASTState> toInput = new TemplateTileInput<>("$to");

    @Override
    public RootAgeValue applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
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

        // we add the prior as likelihood to the beast state

        beastState.addLikelihoodDistribution(prior, "rootCalibration");

        // Return the current root age, rather than its initial value.

        RootAgeValue value = new RootAgeValue();
        beastState.setInput(value, value.treeInput, tree);
        beastState.addCalculationNode(
                value, new TypeToken<RootAgeValue>() {}, this.getId("rootAge", indexVariables, ""));
        beastState.initBEASTObject(value);

        return value;
    }
}
