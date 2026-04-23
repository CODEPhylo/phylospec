package tiles.operators;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.evolution.branchratemodel.Base;
import beast.base.spec.evolution.operator.UpDownOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import tiles.TemplateTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;
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

    TemplateTileInput<Tree> treeInput = new TemplateTileInput<>("$tree", Set.of(Stochasticity.STOCHASTIC));
    TemplateTileInput<Base> branchRateModelInput = new TemplateTileInput<>("$branchRates", Set.of(Stochasticity.STOCHASTIC));
    TemplateTileInput<?> substitutionModelInput = new TemplateTileInput<>("$$qMatrix", false);
    TemplateTileInput<?> partialSiteRateModel = new TemplateTileInput<>("$$siteRates", false);

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
            beastState.setInput(upDownOperator, upDownOperator.m_pWeight, 5.0);
            beastState.setInput(upDownOperator, upDownOperator.scaleFactorInput, 0.75);
            beastState.addOperator(upDownOperator, Set.of(tree, clockRate));
        }

        return null;
    }
}
