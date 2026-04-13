package tiles.distributions;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.substitutionmodel.SubstitutionModel;
import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.branchratemodel.Base;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.evolution.likelihood.TreeLikelihood;
import beast.base.spec.evolution.sitemodel.SiteModel;
import beast.base.spec.inference.parameter.RealScalarParam;
import tiles.MultiAstNodeTile;
import tiling.BEASTState;
import tiling.UnboundDistribution;

public class PhyloCTMCNoRatesTile extends MultiAstNodeTile<UnboundDistribution<Alignment, TreeLikelihood>> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
               PhyloCTMC(
                  tree=$tree,
                  qMatrix=$substitutionModel,
               )
               """;
    }

    MultiAstNodeTileInput<Tree> treeInput = new MultiAstNodeTileInput<>("$tree");
    MultiAstNodeTileInput<SubstitutionModel> substitutionModelInput = new MultiAstNodeTileInput<>("$substitutionModel");

    @Override
    public UnboundDistribution<Alignment, TreeLikelihood> applyTile(BEASTState beastState) {
        Tree tree = this.treeInput.apply(beastState);
        SubstitutionModel substitutionModel = this.substitutionModelInput.apply(beastState);

        // initialize clock model

        Base branchRateModel = new StrictClockModel();
        beastState.setInput(branchRateModel, branchRateModel.meanRateInput, new RealScalarParam<>(1.0, PositiveReal.INSTANCE));

        // initialize site model

        SiteModel siteModel = new SiteModel();
        beastState.setInput(siteModel, siteModel.substModelInput, substitutionModel);

        // initialize tree likelihood

        TreeLikelihood treeLikelihood = new TreeLikelihood();
        beastState.setInput(treeLikelihood, treeLikelihood.treeInput, tree);
        beastState.setInput(treeLikelihood, treeLikelihood.siteModelInput, siteModel);
        beastState.setInput(treeLikelihood, treeLikelihood.branchRateModelInput, branchRateModel);

        return new UnboundDistribution<>(
                treeLikelihood,
                data -> beastState.setInput(treeLikelihood, treeLikelihood.dataInput, data)
        );
    }

}
