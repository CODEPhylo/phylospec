package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import beast.base.core.BEASTInterface;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.operator.AdaptableVarianceMultivariateNormalOperator;
import beast.base.spec.evolution.operator.UpDownOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.util.List;
import org.junit.jupiter.api.Test;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_f0;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_t50;
import popfunc.beast.evolution.populationmodel.StochasticVariableSelection;
import tiles.popfunc.PopFuncTileLibrary;

public class PopFuncOperatorsTest {

    @Test
    public void addsOperatorsProvidedByGompertzF0() {
        GompertzGrowth_f0 model = gompertzF0();
        BEASTState state = stateWithCoalescent(model);

        new PopFuncTileLibrary().configureState(state);

        assertEquals(3, state.operators.size());
        assertEquals(2, state.operators.stream().filter(UpDownOperator.class::isInstance).count());
        assertEquals(
                1,
                state.operators.stream()
                        .filter(AdaptableVarianceMultivariateNormalOperator.class::isInstance)
                        .count());
    }

    @Test
    public void addsOperatorsForModelsInsideStochasticSelection() {
        StochasticVariableSelection selection = new StochasticVariableSelection();
        selection.modelsInput.setValue(List.of(gompertzF0(), gompertzT50()), selection);
        BEASTState state = stateWithCoalescent(selection);

        new PopFuncTileLibrary().configureState(state);

        assertEquals(5, state.operators.size());
        assertEquals(3, state.operators.stream().filter(UpDownOperator.class::isInstance).count());
        assertEquals(
                2,
                state.operators.stream()
                        .filter(AdaptableVarianceMultivariateNormalOperator.class::isInstance)
                        .count());
    }

    private static BEASTState stateWithCoalescent(PopulationFunction population) {
        Tree tree = new Tree();
        tree.setID("tree");
        ((BEASTInterface) population).setID("population");

        Coalescent coalescent = new Coalescent();
        coalescent.popSizeInput.setValue(population, coalescent);
        coalescent.treeInput.setValue(tree, coalescent);

        BEASTState state = new BEASTState("popfunc-operators");
        state.priorDistributions.put(tree, coalescent);
        return state;
    }

    private static GompertzGrowth_f0 gompertzF0() {
        GompertzGrowth_f0 model = new GompertzGrowth_f0();
        model.f0Input.setValue(new RealScalarParam<>(0.2, PositiveReal.INSTANCE), model);
        model.bInput.setValue(new RealScalarParam<>(0.3, PositiveReal.INSTANCE), model);
        model.N0Input.setValue(new RealScalarParam<>(1000.0, PositiveReal.INSTANCE), model);
        return model;
    }

    private static GompertzGrowth_t50 gompertzT50() {
        GompertzGrowth_t50 model = new GompertzGrowth_t50();
        model.t50Input.setValue(new RealScalarParam<>(5.0, NonNegativeReal.INSTANCE), model);
        model.bInput.setValue(new RealScalarParam<>(0.3, PositiveReal.INSTANCE), model);
        model.NInfinityInput.setValue(new RealScalarParam<>(1000.0, PositiveReal.INSTANCE), model);
        return model;
    }
}
