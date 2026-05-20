package tiles.distributions;

import dr.evolution.alignment.Alignment;
import dr.evomodel.branchmodel.HomogeneousBranchModel;
import dr.evomodel.branchratemodel.BranchRateModel;
import dr.evomodel.branchratemodel.StrictClockBranchRates;
import dr.evomodel.siteratemodel.GammaSiteRateModel;
import dr.evomodel.substmodel.SubstitutionModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.Partial;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BeastXPhyloCTMCLikelihoodSpec;
import tiling.BeastXState;
import tiling.UnboundDistribution;

import java.util.IdentityHashMap;

public class PhyloCTMCTile extends GeneratorTile<
        UnboundDistribution<Alignment>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "PhyloCTMC";
    }

    GeneratorTileInput<TreeModel, BeastXState> treeInput =
            new GeneratorTileInput<>("tree");

    GeneratorTileInput<SubstitutionModel, BeastXState> substitutionModelInput =
            new GeneratorTileInput<>("qMatrix");

    GeneratorTileInput<BranchRateModel, BeastXState> branchRateModelInput =
            new GeneratorTileInput<>("branchRates", false);

    GeneratorTileInput<Partial<GammaSiteRateModel, SubstitutionModel>, BeastXState> siteRateModelInput =
            new GeneratorTileInput<>("siteRates", false);

    @Override
    public UnboundDistribution<Alignment> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        TreeModel tree =
                this.treeInput.apply(beastState, indexVariables);

        SubstitutionModel substitutionModel =
                this.substitutionModelInput.apply(beastState, indexVariables);

        BranchRateModel branchRateModel =
                this.branchRateModelInput.apply(beastState, indexVariables);

        Partial<GammaSiteRateModel, SubstitutionModel> partialSiteRateModel =
                this.siteRateModelInput.apply(beastState, indexVariables);

        GammaSiteRateModel siteRateModel;
        if (partialSiteRateModel != null) {
            siteRateModel = partialSiteRateModel.complete(substitutionModel);
        } else {
            siteRateModel = new GammaSiteRateModel("siteRateModel");
            siteRateModel.setSubstitutionModel(substitutionModel);
        }

        HomogeneousBranchModel branchModel =
                new HomogeneousBranchModel(substitutionModel);

        if (branchRateModel == null) {
            branchRateModel =
                    new StrictClockBranchRates(new Parameter.Default(1.0));
        }

        BranchRateModel finalBranchRateModel = branchRateModel;

        return new UnboundDistribution<>((observedAlignment, id) -> {
            BeastXPhyloCTMCLikelihoodSpec likelihoodSpec =
                    new BeastXPhyloCTMCLikelihoodSpec(
                            id,
                            observedAlignment,
                            tree,
                            branchModel,
                            siteRateModel,
                            finalBranchRateModel
                    );

            beastState.addLikelihoodDistribution(likelihoodSpec, id);
        });
    }
}