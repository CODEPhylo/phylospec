package beastconfig;

import beast.base.evolution.operator.Exchange;
import beast.base.evolution.operator.WilsonBalding;
import beast.base.evolution.operator.kernel.BactrianNodeOperator;
import beast.base.evolution.operator.kernel.BactrianSubtreeSlide;
import beast.base.evolution.tree.Tree;
import beast.base.inference.StateNode;
import beast.base.spec.evolution.operator.ScaleTreeOperator;
import beast.base.spec.inference.operator.BitFlipOperator;
import beast.base.spec.inference.operator.IntRandomWalkOperator;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.operator.SwapOperator;
import beast.base.spec.inference.parameter.BoolVectorParam;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;

/// Selects and adds the appropriate MCMC operators for a given state node.
///
/// Assigns a default set of operators based on the runtime type of the state node.
/// Trees receive a standard suite of tree operators; scalar and vector parameters receive
/// scale, bit-flip, random-walk, or swap operators as appropriate.
public class OperatorSelector {

    /**
     * Adds the default operators for the given state node.
     */
    public static void addDefaultOperators(StateNode stateNode, BEASTState beastState) {
        if (stateNode instanceof Tree tree) {
            ScaleTreeOperator scaleTreeOperator = new ScaleTreeOperator();
            beastState.setInput(scaleTreeOperator, scaleTreeOperator.treeInput, tree);
            beastState.setInput(scaleTreeOperator, scaleTreeOperator.scaleFactorInput, 0.1);
            beastState.setInput(scaleTreeOperator, scaleTreeOperator.m_pWeight, 5.0);
            beastState.addOperator(scaleTreeOperator, stateNode);

            BactrianSubtreeSlide bactrianSubtreeSlideOperator = new BactrianSubtreeSlide();
            beastState.setInput(bactrianSubtreeSlideOperator, bactrianSubtreeSlideOperator.treeInput, tree);
            beastState.setInput(bactrianSubtreeSlideOperator, bactrianSubtreeSlideOperator.m_pWeight, 15.0);
            beastState.addOperator(bactrianSubtreeSlideOperator, stateNode);

            BactrianNodeOperator bactrianNodeOperator = new BactrianNodeOperator();
            beastState.setInput(bactrianNodeOperator, bactrianNodeOperator.treeInput, tree);
            beastState.setInput(bactrianNodeOperator, bactrianNodeOperator.m_pWeight, 30.0);
            beastState.addOperator(bactrianNodeOperator, stateNode);

            Exchange narrowExchangeOperator = new Exchange();
            beastState.setInput(narrowExchangeOperator, narrowExchangeOperator.treeInput, tree);
            beastState.setInput(narrowExchangeOperator, narrowExchangeOperator.m_pWeight, 15.0);
            beastState.setInput(narrowExchangeOperator, narrowExchangeOperator.isNarrowInput, true);
            beastState.addOperator(narrowExchangeOperator, stateNode);

            Exchange wideExchangeOperator = new Exchange();
            beastState.setInput(wideExchangeOperator, wideExchangeOperator.treeInput, tree);
            beastState.setInput(wideExchangeOperator, wideExchangeOperator.m_pWeight, 5.0);
            beastState.setInput(wideExchangeOperator, wideExchangeOperator.isNarrowInput, false);
            beastState.addOperator(wideExchangeOperator, stateNode);

            WilsonBalding wilsonBaldingOperator = new WilsonBalding();
            beastState.setInput(wilsonBaldingOperator, wilsonBaldingOperator.treeInput, tree);
            beastState.setInput(wilsonBaldingOperator, wilsonBaldingOperator.m_pWeight, 5.0);
            beastState.addOperator(wilsonBaldingOperator, stateNode);
        }

        if (stateNode instanceof RealScalarParam<?> realScalar) {
            ScaleOperator scaleOperator = new ScaleOperator();
            beastState.setInput(scaleOperator, scaleOperator.parameterInput, realScalar);
            beastState.setInput(scaleOperator, scaleOperator.m_pWeight, 5.0);
            beastState.addOperator(scaleOperator, stateNode);
        }

        if (stateNode instanceof RealVectorParam<?> realVector) {
            ScaleOperator scaleOperator = new ScaleOperator();
            beastState.setInput(scaleOperator, scaleOperator.parameterInput, realVector);
            beastState.setInput(scaleOperator, scaleOperator.m_pWeight, 5.0);
            beastState.addOperator(scaleOperator, stateNode);
        }

        if (stateNode instanceof BoolVectorParam bool) {
            BitFlipOperator bitFlipOperator = new BitFlipOperator();
            beastState.setInput(bitFlipOperator, bitFlipOperator.parameterInput, bool);
            beastState.setInput(bitFlipOperator, bitFlipOperator.m_pWeight, 5.0);
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
