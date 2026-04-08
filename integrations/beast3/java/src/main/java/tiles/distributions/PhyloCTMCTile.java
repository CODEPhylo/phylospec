package tiles.distributions;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.substitutionmodel.SubstitutionModel;
import beast.base.evolution.tree.Tree;
import beast.base.spec.evolution.branchratemodel.Base;
import beast.base.spec.evolution.likelihood.TreeLikelihood;
import beast.base.spec.evolution.sitemodel.SiteModel;
import tiles.MultiAstNodeTile;
import tiling.*;

public class PhyloCTMCTile extends MultiAstNodeTile<UnboundDistribution<Alignment, TreeLikelihood>> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
               PhyloCTMC(
                  tree=$tree,
                  branchRates~$branchRateModel,
                  qMatrix=$substitutionModel,
                  siteRates~$partialSiteRateModel
               )
               """;
    }

    TileInput<Tree> treeInput = new TileInput<>("$tree");
    TileInput<Base> branchRateModelInput = new TileInput<>("$branchRateModel");
    TileInput<SubstitutionModel> substitutionModelInput = new TileInput<>("$substitutionModel");
    TileInput<Partial<SiteModel, SubstitutionModel>> partialSiteRateModel = new TileInput<>("$partialSiteRateModel");

    @Override
    public UnboundDistribution<Alignment, TreeLikelihood> applyTile(BEASTState beastState) {
        Tree tree = this.treeInput.apply(beastState);
        Base branchRateModel = this.branchRateModelInput.apply(beastState);
        SubstitutionModel substitutionModel = this.substitutionModelInput.apply(beastState);
        Partial<SiteModel, SubstitutionModel> partialSiteModel = this.partialSiteRateModel.apply(beastState);

        SiteModel siteModel = partialSiteModel.complete(substitutionModel);

        TreeLikelihood treeLikelihood = new TreeLikelihood();
        beastState.setInput(treeLikelihood, treeLikelihood.treeInput, tree);
        beastState.setInput(treeLikelihood, treeLikelihood.siteModelInput, siteModel);
        beastState.setInput(treeLikelihood, treeLikelihood.branchRateModelInput, branchRateModel);

        return new UnboundDistribution<>(
                treeLikelihood,
                data -> beastState.setInput(treeLikelihood, treeLikelihood.dataInput, data)
        );
    }

    @Override
    protected Tile<?> createInstance() {
        return new PhyloCTMCTile();
    }
}
