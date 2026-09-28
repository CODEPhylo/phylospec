package tiles.distributions;

import beast.base.core.Input;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.substitutionmodel.SubstitutionModel;
import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.branchratemodel.Base;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.evolution.likelihood.TreeLikelihood;
import beast.base.spec.evolution.sitemodel.SiteModel;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.inference.parameter.VectorElement;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import beastobjects.ScaledScalar;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.Partial;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.UnboundDistribution;

public class PhyloCTMCTile extends GeneratorTile<UnboundDistribution<Alignment, TreeLikelihood>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "PhyloCTMC";
    }

    GeneratorTileInput<Tree, BEASTState> treeInput = new GeneratorTileInput<>("tree");
    GeneratorTileInput<SubstitutionModel, BEASTState> substitutionModelInput =
            new GeneratorTileInput<>("qMatrix", true);
    GeneratorTileInput<Base, BEASTState> branchRateModelInput = new GeneratorTileInput<>("branchRates", false);
    GeneratorTileInput<Partial<SiteModel, SubstitutionModel>, BEASTState> partialSiteRateModel =
            new GeneratorTileInput<>("siteRates", false);
    GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> rateInput =
            new GeneratorTileInput<>("rate", false);

    @Override
    public UnboundDistribution<Alignment, TreeLikelihood> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Tree tree = this.treeInput.apply(beastState, indexVariables);
        SubstitutionModel substitutionModel = this.substitutionModelInput.apply(beastState, indexVariables);
        Base branchRateModel = this.branchRateModelInput.apply(beastState, indexVariables);
        Partial<SiteModel, SubstitutionModel> partialSiteModel =
                this.partialSiteRateModel.apply(beastState, indexVariables);
        RealScalar<? extends PositiveReal> rate = this.rateInput.apply(beastState, indexVariables);

        // initialize clock model

        if (branchRateModel == null) {
            branchRateModel = new StrictClockModel();
            beastState.setInput(
                    branchRateModel, branchRateModel.meanRateInput, new RealScalarParam<>(1.0, PositiveReal.INSTANCE));
        }

        // initialize site model

        SiteModel siteModel;
        if (partialSiteModel != null) {
            siteModel = partialSiteModel.complete(substitutionModel);
        } else {
            siteModel = new SiteModel();
            beastState.setInput(siteModel, siteModel.substModelInput, substitutionModel);
        }

        // the partition's relative rate: BEAST's per-site-model mutation rate

        if (rate != null) {
            setMutationRate(beastState, siteModel, rate);
        }

        // initialize tree likelihood

        TreeLikelihood treeLikelihood = new TreeLikelihood();
        beastState.setInput(treeLikelihood, treeLikelihood.treeInput, tree);
        beastState.setInput(treeLikelihood, treeLikelihood.siteModelInput, siteModel);
        beastState.setInput(treeLikelihood, treeLikelihood.branchRateModelInput, branchRateModel);

        return new UnboundDistribution<>(
                treeLikelihood, data -> beastState.setInput(treeLikelihood, treeLikelihood.dataInput, data));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setMutationRate(BEASTState beastState, SiteModel siteModel, RealScalar<?> rate) {
        beastState.setInput(siteModel, (Input) siteModel.muParameterInput, rate);

        // a rate built from one element of a simplex makes that simplex partition weights
        if (rate instanceof ScaledScalar scaled
                && scaled.scalarInput.get() instanceof VectorElement<?> element
                && element.vectorInput.get() instanceof SimplexParam simplex) {
            OperatorSelector.tuneForPartitionRates(beastState, simplex);
        }
    }
}
