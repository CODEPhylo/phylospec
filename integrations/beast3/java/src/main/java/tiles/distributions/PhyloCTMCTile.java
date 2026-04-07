package tiles.distributions;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.branchratemodel.BranchRateModel;
import beast.base.evolution.substitutionmodel.SubstitutionModel;
import beast.base.evolution.tree.Tree;
import beast.base.spec.evolution.likelihood.TreeLikelihood;
import beast.base.spec.evolution.sitemodel.SiteModel;
import tiles.MultiAstNodeTile;
import tiling.BEASTState;
import tiling.Partial;
import tiling.Tile;

public class PhyloCTMCTile extends MultiAstNodeTile<Alignment> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
               Alignment alignment ~ PhyloCTMC(
                  tree=$tree,
                  branchRates~$branchRateModel,
                  qMatrix=$substitutionModel,
                  siteRates~$partialSiteRateModel
               ) observed as $data
               """;
    }

    TileInput<Tree> treeInput = new TileInput<>("$tree");
    TileInput<BranchRateModel> branchRateModelInput = new TileInput<>("$branchRateModel");
    TileInput<SubstitutionModel> substitutionModelInput = new TileInput<>("$substitutionModel");
    TileInput<Partial<SiteModel, SubstitutionModel>> partialSiteRateModel = new TileInput<>("$partialSiteRateModel");
    TileInput<Alignment> dataInput = new TileInput<>("$data");

    @Override
    public Alignment applyTile(BEASTState beastState) {
        Tree tree = this.treeInput.apply(beastState);
        BranchRateModel branchRateModel = this.branchRateModelInput.apply(beastState);
        SubstitutionModel substitutionModel = this.substitutionModelInput.apply(beastState);
        Partial<SiteModel, SubstitutionModel> partialSiteModel = this.partialSiteRateModel.apply(beastState);
        Alignment data = this.dataInput.apply(beastState);

        TreeLikelihood treeLikelihood = new TreeLikelihood();
        treeLikelihood.setInputValue("data", data);
        treeLikelihood.setInputValue("tree", tree);
        treeLikelihood.setInputValue("siteModel", partialSiteModel.complete(substitutionModel));
        treeLikelihood.setInputValue("branchRateModel", branchRateModel);

        beastState.addDistribution(null, treeLikelihood);

        return data;
    }

    @Override
    protected Tile<?> createInstance() {
        return new PhyloCTMCTile();
    }
}
