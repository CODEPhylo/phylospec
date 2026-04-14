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
import tiling.*;

public class PhyloCTMCTile extends MultiAstNodeTile<UnboundDistribution<Alignment, TreeLikelihood>> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
               PhyloCTMC(
                  tree=$tree,
                  qMatrix=$substitutionModel,
                  branchRates~$$branchRateModel,
                  siteRates~$$partialSiteRateModel
               )
               """;
    }

    MultiAstNodeTileInput<Tree> treeInput = new MultiAstNodeTileInput<>("$tree");
    MultiAstNodeTileInput<SubstitutionModel> substitutionModelInput = new MultiAstNodeTileInput<>("$substitutionModel", true);
    MultiAstNodeTileInput<Base> branchRateModelInput = new MultiAstNodeTileInput<>("$$branchRateModel", false);
    MultiAstNodeTileInput<Partial<SiteModel, SubstitutionModel>> partialSiteRateModel = new MultiAstNodeTileInput<>("$$partialSiteRateModel", false);

    @Override
    public UnboundDistribution<Alignment, TreeLikelihood> applyTile(BEASTState beastState) {
        Tree tree = this.treeInput.apply(beastState);
        SubstitutionModel substitutionModel = this.substitutionModelInput.apply(beastState);
        Base branchRateModel = this.branchRateModelInput.apply(beastState);
        Partial<SiteModel, SubstitutionModel> partialSiteModel = this.partialSiteRateModel.apply(beastState);

        // initialize clock model

        if (branchRateModel == null) {
            branchRateModel = new StrictClockModel();
            beastState.setInput(branchRateModel, branchRateModel.meanRateInput, new RealScalarParam<>(1.0, PositiveReal.INSTANCE));
        }

        // initialize site model

        SiteModel siteModel;
        if (partialSiteModel != null) {
            siteModel = partialSiteModel.complete(substitutionModel);
        } else {
            siteModel = new SiteModel();
            beastState.setInput(siteModel, siteModel.substModelInput, substitutionModel);
        }

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
