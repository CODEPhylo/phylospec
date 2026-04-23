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
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import tiles.TemplateTile;
import tiling.*;

import java.util.IdentityHashMap;

public class PhyloCTMCTile extends GeneratorTile<UnboundDistribution<Alignment, TreeLikelihood>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "PhyloCTMC";
    }

    GeneratorTileInput<Tree> treeInput = new GeneratorTileInput<>("tree");
    GeneratorTileInput<SubstitutionModel> substitutionModelInput = new GeneratorTileInput<>("qMatrix", true);
    GeneratorTileInput<Base> branchRateModelInput = new GeneratorTileInput<>("branchRates", false);
    GeneratorTileInput<Partial<SiteModel, SubstitutionModel>> partialSiteRateModel = new GeneratorTileInput<>("siteRates", false);

    @Override
    public UnboundDistribution<Alignment, TreeLikelihood> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Tree tree = this.treeInput.apply(beastState, indexVariables);
        SubstitutionModel substitutionModel = this.substitutionModelInput.apply(beastState, indexVariables);
        Base branchRateModel = this.branchRateModelInput.apply(beastState, indexVariables);
        Partial<SiteModel, SubstitutionModel> partialSiteModel = this.partialSiteRateModel.apply(beastState, indexVariables);

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
