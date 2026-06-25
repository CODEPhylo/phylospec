package beastconfig;

import beast.base.evolution.operator.Exchange;
import beast.base.evolution.operator.WilsonBalding;
import beast.base.evolution.operator.kernel.BactrianNodeOperator;
import beast.base.evolution.operator.kernel.BactrianSubtreeSlide;
import beast.base.evolution.tree.Tree;
import beast.base.inference.StateNode;
import beast.base.spec.evolution.operator.ScaleTreeOperator;
import beast.base.spec.inference.operator.BitFlipOperator;
import beast.base.spec.inference.operator.DeltaExchangeOperator;
import beast.base.spec.inference.operator.IntRandomWalkOperator;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.operator.SwapOperator;
import beast.base.spec.inference.parameter.BoolVectorParam;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.inference.parameter.SimplexParam;

/// Selects and adds the appropriate MCMC operators for a given state node.
///
/// Assigns a default set of operators based on the runtime type of the state node.
/// Trees receive a standard suite of tree operators; scalar and vector parameters receive
/// scale, bit-flip, random-walk, swap, or simplex-preserving exchange operators as appropriate.
public class OperatorSelector {

    private static final double PARAMETER_OPERATOR_WEIGHT = 1.0;
    private static final double PARAMETER_SCALE_FACTOR = 0.75;

    private static final double SIMPLEX_OPERATOR_WEIGHT = 1.0;
    private static final double SIMPLEX_DELTA = 0.01;

    private static final double TREE_SCALE_WEIGHT = 5.0;
    private static final double TREE_SCALE_FACTOR = 0.75;

    private static final double TREE_SUBTREE_SLIDE_WEIGHT = 15.0;
    private static final double TREE_NODE_OPERATOR_WEIGHT = 30.0;
    private static final double TREE_NARROW_EXCHANGE_WEIGHT = 15.0;
    private static final double TREE_WIDE_EXCHANGE_WEIGHT = 5.0;
    private static final double TREE_WILSON_BALDING_WEIGHT = 5.0;

    /**
     * Adds the default operators for the given state node.
     */
    public static void addDefaultOperators(StateNode stateNode, BEASTState beastState) {
        if (stateNode instanceof Tree tree) {
            ScaleTreeOperator scaleTreeOperator = new ScaleTreeOperator();
            beastState.setInput(scaleTreeOperator, scaleTreeOperator.treeInput, tree);
            beastState.setInput(scaleTreeOperator, scaleTreeOperator.scaleFactorInput, TREE_SCALE_FACTOR);
            beastState.setInput(scaleTreeOperator, scaleTreeOperator.m_pWeight, TREE_SCALE_WEIGHT);
            beastState.addOperator(scaleTreeOperator, stateNode);

            BactrianSubtreeSlide bactrianSubtreeSlideOperator = new BactrianSubtreeSlide();
            beastState.setInput(bactrianSubtreeSlideOperator, bactrianSubtreeSlideOperator.treeInput, tree);
            beastState.setInput(bactrianSubtreeSlideOperator, bactrianSubtreeSlideOperator.m_pWeight, TREE_SUBTREE_SLIDE_WEIGHT);
            beastState.addOperator(bactrianSubtreeSlideOperator, stateNode);

            BactrianNodeOperator bactrianNodeOperator = new BactrianNodeOperator();
            beastState.setInput(bactrianNodeOperator, bactrianNodeOperator.treeInput, tree);
            beastState.setInput(bactrianNodeOperator, bactrianNodeOperator.m_pWeight, TREE_NODE_OPERATOR_WEIGHT);
            beastState.addOperator(bactrianNodeOperator, stateNode);

            Exchange narrowExchangeOperator = new Exchange();
            beastState.setInput(narrowExchangeOperator, narrowExchangeOperator.treeInput, tree);
            beastState.setInput(narrowExchangeOperator, narrowExchangeOperator.m_pWeight, TREE_NARROW_EXCHANGE_WEIGHT);
            beastState.setInput(narrowExchangeOperator, narrowExchangeOperator.isNarrowInput, true);
            beastState.addOperator(narrowExchangeOperator, stateNode);

            Exchange wideExchangeOperator = new Exchange();
            beastState.setInput(wideExchangeOperator, wideExchangeOperator.treeInput, tree);
            beastState.setInput(wideExchangeOperator, wideExchangeOperator.m_pWeight, TREE_WIDE_EXCHANGE_WEIGHT);
            beastState.setInput(wideExchangeOperator, wideExchangeOperator.isNarrowInput, false);
            beastState.addOperator(wideExchangeOperator, stateNode);

            WilsonBalding wilsonBaldingOperator = new WilsonBalding();
            beastState.setInput(wilsonBaldingOperator, wilsonBaldingOperator.treeInput, tree);
            beastState.setInput(wilsonBaldingOperator, wilsonBaldingOperator.m_pWeight, TREE_WILSON_BALDING_WEIGHT);
            beastState.addOperator(wilsonBaldingOperator, stateNode);
        }

        if (stateNode instanceof RealScalarParam<?> realScalar) {
            ScaleOperator scaleOperator = new ScaleOperator();
            beastState.setInput(scaleOperator, scaleOperator.parameterInput, realScalar);
            beastState.setInput(scaleOperator, scaleOperator.scaleFactorInput, PARAMETER_SCALE_FACTOR);
            beastState.setInput(scaleOperator, scaleOperator.m_pWeight, PARAMETER_OPERATOR_WEIGHT);
            beastState.addOperator(scaleOperator, stateNode);
        }

        if (stateNode instanceof SimplexParam simplex) {
            DeltaExchangeOperator deltaExchangeOperator = new DeltaExchangeOperator();
            beastState.setInput(deltaExchangeOperator, deltaExchangeOperator.rvparameterInput, simplex);
            beastState.setInput(deltaExchangeOperator, deltaExchangeOperator.deltaInput, SIMPLEX_DELTA);
            beastState.setInput(deltaExchangeOperator, deltaExchangeOperator.m_pWeight, SIMPLEX_OPERATOR_WEIGHT);
            beastState.addOperator(deltaExchangeOperator, stateNode);
        }

        if (stateNode instanceof RealVectorParam<?> realVector && !(stateNode instanceof SimplexParam)) {
            ScaleOperator scaleOperator = new ScaleOperator();
            beastState.setInput(scaleOperator, scaleOperator.parameterInput, realVector);
            beastState.setInput(scaleOperator, scaleOperator.scaleFactorInput, PARAMETER_SCALE_FACTOR);
            beastState.setInput(scaleOperator, scaleOperator.m_pWeight, PARAMETER_OPERATOR_WEIGHT);
            beastState.addOperator(scaleOperator, stateNode);
        }

        if (stateNode instanceof BoolVectorParam bool) {
            BitFlipOperator bitFlipOperator = new BitFlipOperator();
            beastState.setInput(bitFlipOperator, bitFlipOperator.parameterInput, bool);
            beastState.setInput(bitFlipOperator, bitFlipOperator.m_pWeight, PARAMETER_OPERATOR_WEIGHT);
            beastState.addOperator(bitFlipOperator, stateNode);
        }

        if (stateNode instanceof IntVectorParam<?> intVector) {
            IntRandomWalkOperator intRandomWalkOperator = new IntRandomWalkOperator();
            beastState.setInput(intRandomWalkOperator, intRandomWalkOperator.parameterInput, intVector);
            beastState.setInput(intRandomWalkOperator, intRandomWalkOperator.windowSizeInput, 1);
            beastState.setInput(intRandomWalkOperator, intRandomWalkOperator.m_pWeight, 10.0);
            beastState.addOperator(intRandomWalkOperator, stateNode);

            SwapOperator swapOperator = new SwapOperator();
            swapOperator.intparameterInput.setValue(intVector, swapOperator);
            beastState.setInput(swapOperator, swapOperator.m_pWeight, 10.0);
            beastState.addOperator(swapOperator, stateNode);
        }
    }
}