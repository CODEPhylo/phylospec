package operators;

import beast.base.evolution.operator.Exchange;
import beast.base.evolution.operator.WilsonBalding;
import beast.base.evolution.operator.kernel.BactrianNodeOperator;
import beast.base.evolution.operator.kernel.BactrianSubtreeSlide;
import beast.base.evolution.tree.Tree;
import beast.base.inference.StateNode;
import beast.base.spec.evolution.operator.ScaleTreeOperator;
import beast.base.spec.inference.operator.BitFlipOperator;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.parameter.BoolVectorParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import tiling.BEASTState;

public class OperatorSelector {

    public static <T extends StateNode> void addOperators(T stateNode, BEASTState beastState) {
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
            beastState.setInput(narrowExchangeOperator, narrowExchangeOperator.isNarrowInput, false);
            beastState.addOperator(narrowExchangeOperator, stateNode);

            Exchange wideExchangeOperator = new Exchange();
            beastState.setInput(wideExchangeOperator, wideExchangeOperator.treeInput, tree);
            beastState.setInput(wideExchangeOperator, wideExchangeOperator.m_pWeight, 5.0);
            beastState.setInput(wideExchangeOperator, wideExchangeOperator.isNarrowInput, true);
            beastState.addOperator(wideExchangeOperator, stateNode);

            WilsonBalding wilsonBaldingOperator = new WilsonBalding();
            beastState.setInput(wilsonBaldingOperator, wilsonBaldingOperator.treeInput, tree);
            beastState.setInput(wilsonBaldingOperator, wilsonBaldingOperator.m_pWeight, 5.0);
            beastState.addOperator(wilsonBaldingOperator, stateNode);
        }

        if (stateNode instanceof RealScalarParam<?> scalable) {
            ScaleOperator scaleOperator = new ScaleOperator();
            beastState.setInput(scaleOperator, scaleOperator.parameterInput, scalable);
            beastState.setInput(scaleOperator, scaleOperator.m_pWeight, 5.0);
            beastState.addOperator(scaleOperator, stateNode);
        }

        if (stateNode instanceof RealVectorParam<?> scalable) {
            ScaleOperator scaleOperator = new ScaleOperator();
            beastState.setInput(scaleOperator, scaleOperator.parameterInput, scalable);
            beastState.setInput(scaleOperator, scaleOperator.m_pWeight, 5.0);
            beastState.addOperator(scaleOperator, stateNode);
        }

        if (stateNode instanceof BoolVectorParam bool) {
            BitFlipOperator bitFlipOperator = new BitFlipOperator();
            beastState.setInput(bitFlipOperator, bitFlipOperator.parameterInput, bool);
            beastState.setInput(bitFlipOperator, bitFlipOperator.m_pWeight, 5.0);
            beastState.addOperator(bitFlipOperator, stateNode);
        }

    }

}
