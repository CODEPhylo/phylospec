package operators;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.branchratemodel.Base;
import beast.base.spec.evolution.operator.UpDownOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import tiles.MultiAstNodeTile;
import tiling.BEASTState;

import java.util.List;
import java.util.Set;

public class BranchRateTreeUpDownOperatorTile extends MultiAstNodeTile<Void> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
                Any alignment ~ PhyloCTMC(
                    tree=$tree,
                    qMatrix=$qMatrix,
                    branchRates~$branchRates,
                    siteRates~$siteRates
                )
                """;
    }

    MultiAstNodeTileInput<Tree> treeInput = new MultiAstNodeTileInput<>("$tree");
    MultiAstNodeTileInput<Base> branchRateModelInput = new MultiAstNodeTileInput<>("$branchRates");
    MultiAstNodeTileInput<?> substitutionModelInput = new MultiAstNodeTileInput<>("$qMatrix");
    MultiAstNodeTileInput<?> partialSiteRateModel = new MultiAstNodeTileInput<>("$siteRates");

    @Override
    protected Void applyTile(BEASTState beastState) {
        Tree tree = this.treeInput.apply(beastState);
        Base branchRateModel = this.branchRateModelInput.apply(beastState);

        if (!(branchRateModel.meanRateInput.get() instanceof RealScalarParam<PositiveReal> clockRate)) {
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
