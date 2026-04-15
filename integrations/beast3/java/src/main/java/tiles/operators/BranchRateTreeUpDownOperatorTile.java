package tiles.operators;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.evolution.branchratemodel.Base;
import beast.base.spec.evolution.operator.UpDownOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import tiles.TemplateTile;
import beastconfig.BEASTState;

import java.util.List;
import java.util.Set;

public class BranchRateTreeUpDownOperatorTile extends TemplateTile<Void> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                Any alignment ~ PhyloCTMC(
                    tree=$tree,
                    qMatrix=$$qMatrix,
                    branchRates~$branchRates,
                    siteRates~$$siteRates
                )
                """;
    }

    TemplateTileInput<Tree> treeInput = new TemplateTileInput<>("$tree");
    TemplateTileInput<Base> branchRateModelInput = new TemplateTileInput<>("$branchRates");
    TemplateTileInput<?> substitutionModelInput = new TemplateTileInput<>("$$qMatrix", false);
    TemplateTileInput<?> partialSiteRateModel = new TemplateTileInput<>("$$siteRates", false);

    @Override
    protected Void applyTile(BEASTState beastState) {
        Tree tree = this.treeInput.apply(beastState);
        Base branchRateModel = this.branchRateModelInput.apply(beastState);

        if (!(branchRateModel.meanRateInput.get() instanceof RealScalarParam<? extends NonNegativeReal> clockRate)) {
            return null;
        }

        UpDownOperator upDownOperator = new UpDownOperator();
        beastState.setInput(upDownOperator, upDownOperator.downInput, List.of(tree));
        beastState.setInput(upDownOperator, upDownOperator.upInput, List.of(clockRate));
        beastState.setInput(upDownOperator, upDownOperator.m_pWeight, 5.0);
        beastState.setInput(upDownOperator, upDownOperator.scaleFactorInput, 0.75);
        beastState.addOperator(upDownOperator, Set.of(tree, clockRate));

        return null;
    }
}
