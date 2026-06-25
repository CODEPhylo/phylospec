package tiles.operators;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.evolution.branchratemodel.Base;
import beast.base.spec.evolution.operator.UpDownOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.tiling.tiles.TemplateTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class BranchRateTreeUpDownOperatorTile extends TemplateTile<Void, BEASTState> {

    private static final double TREE_CLOCK_UP_DOWN_WEIGHT = 5.0;
    private static final double TREE_CLOCK_UP_DOWN_SCALE_FACTOR = 0.75;

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

    TemplateTileInput<Tree, BEASTState> treeInput = new TemplateTileInput<>("$tree", Set.of(Stochasticity.STOCHASTIC));
    TemplateTileInput<Base, BEASTState> branchRateModelInput = new TemplateTileInput<>("$branchRates", Set.of(Stochasticity.STOCHASTIC));
    TemplateTileInput<?, BEASTState> substitutionModelInput = new TemplateTileInput<>("$$qMatrix", false);
    TemplateTileInput<?, BEASTState> partialSiteRateModel = new TemplateTileInput<>("$$siteRates", false);

    @Override
    protected Void applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Tree tree = this.treeInput.apply(beastState, indexVariables);
        Base branchRateModel = this.branchRateModelInput.apply(beastState, indexVariables);

        if (!(branchRateModel.meanRateInput.get() instanceof RealScalarParam<? extends NonNegativeReal> clockRate)) {
            return null;
        }

        if (beastState.priorDistributions.containsKey(tree) && beastState.priorDistributions.containsKey(clockRate)) {
            UpDownOperator upDownOperator = new UpDownOperator();
            beastState.setInput(upDownOperator, upDownOperator.downInput, List.of(tree));
            beastState.setInput(upDownOperator, upDownOperator.upInput, List.of(clockRate));
            beastState.setInput(upDownOperator, upDownOperator.m_pWeight, TREE_CLOCK_UP_DOWN_WEIGHT);
            beastState.setInput(upDownOperator, upDownOperator.scaleFactorInput, TREE_CLOCK_UP_DOWN_SCALE_FACTOR);
            beastState.addOperator(upDownOperator, Set.of(tree, clockRate));
        }

        return null;
    }
}