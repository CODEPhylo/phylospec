package tiles.packages.sampledancestors;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import sa.evolution.operators.LeafToSampledAncestorJump;
import sa.evolution.operators.SAExchange;
import sa.evolution.operators.SAScaleOperator;
import sa.evolution.operators.SAUniform;
import sa.evolution.operators.SAWilsonBalding;

/** Selects the tree operators required for sampled-ancestor analyses. */
final class SampledAncestorOperatorSelector {

    private SampledAncestorOperatorSelector() {}

    static void addDefaultOperators(
            Tree tree,
            RealScalar<? extends UnitInterval> removalProbability,
            BEASTState beastState) {
        LeafToSampledAncestorJump leafToAncestor = new LeafToSampledAncestorJump();
        beastState.setInput(leafToAncestor, leafToAncestor.treeInput, tree);
        beastState.setInput(leafToAncestor, leafToAncestor.rInput, removalProbability);
        beastState.setInput(leafToAncestor, leafToAncestor.m_pWeight, 10.0);
        beastState.addOperator(leafToAncestor, tree);

        SAWilsonBalding wilsonBalding = new SAWilsonBalding();
        beastState.setInput(wilsonBalding, wilsonBalding.treeInput, tree);
        beastState.setInput(wilsonBalding, wilsonBalding.rInput, removalProbability);
        beastState.setInput(wilsonBalding, wilsonBalding.m_pWeight, 10.0);
        beastState.addOperator(wilsonBalding, tree);

        addExchange(tree, true, 10.0, beastState);
        addExchange(tree, false, 10.0, beastState);

        SAUniform uniform = new SAUniform();
        beastState.setInput(uniform, uniform.treeInput, tree);
        beastState.setInput(uniform, uniform.m_pWeight, 20.0);
        beastState.addOperator(uniform, tree);

        addScale(tree, true, 1.0, beastState);
        addScale(tree, false, 3.0, beastState);
    }

    private static void addExchange(
            Tree tree, boolean narrow, double weight, BEASTState beastState) {
        SAExchange exchange = new SAExchange();
        beastState.setInput(exchange, exchange.treeInput, tree);
        beastState.setInput(exchange, exchange.isNarrowInput, narrow);
        beastState.setInput(exchange, exchange.m_pWeight, weight);
        beastState.addOperator(exchange, tree);
    }

    private static void addScale(
            Tree tree, boolean rootOnly, double weight, BEASTState beastState) {
        SAScaleOperator scale = new SAScaleOperator();
        beastState.setInput(scale, scale.treeInput, tree);
        beastState.setInput(scale, scale.rootOnlyInput, rootOnly);
        beastState.setInput(scale, scale.scaleFactorInput, 0.95);
        beastState.setInput(scale, scale.m_pWeight, weight);
        beastState.addOperator(scale, tree);
    }
}
