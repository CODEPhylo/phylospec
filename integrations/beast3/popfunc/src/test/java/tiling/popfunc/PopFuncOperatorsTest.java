package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import beast.base.core.BEASTInterface;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.inference.StateNode;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.operator.AdaptableVarianceMultivariateNormalOperator;
import beast.base.spec.evolution.operator.UpDownOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.tiling.TypeToken;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_f0;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_t50;
import popfunc.beast.evolution.populationmodel.StochasticVariableSelection;
import tiles.popfunc.PopFuncTileLibrary;

public class PopFuncOperatorsTest {

    @Test
    public void addsOperatorsProvidedByGompertzF0() {
        GompertzGrowth_f0 model = gompertzF0();
        BEASTState state = stateWithCoalescent(model);
        register(state, "f0", (StateNode) model.f0Input.get());
        register(state, "b", (StateNode) model.bInput.get());

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
        GompertzGrowth_f0 f0 = gompertzF0();
        GompertzGrowth_t50 t50 = gompertzT50();
        StochasticVariableSelection selection = new StochasticVariableSelection();
        selection.modelsInput.setValue(List.of(f0, t50), selection);
        BEASTState state = stateWithCoalescent(selection);
        register(state, "f0", (StateNode) f0.f0Input.get());
        register(state, "f0B", (StateNode) f0.bInput.get());
        register(state, "t50", (StateNode) t50.t50Input.get());
        register(state, "t50B", (StateNode) t50.bInput.get());

        new PopFuncTileLibrary().configureState(state);

        assertEquals(5, state.operators.size());
        assertEquals(3, state.operators.stream().filter(UpDownOperator.class::isInstance).count());
        assertEquals(
                2,
                state.operators.stream()
                        .filter(AdaptableVarianceMultivariateNormalOperator.class::isInstance)
                        .count());
    }

    @Test
    public void skipsPackageOperatorsForFixedPopulationParameters() {
        BEASTState state = stateWithCoalescent(gompertzF0());

        new PopFuncTileLibrary().configureState(state);

        assertEquals(0, state.operators.size());
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
        register(state, "tree", tree);
        return state;
    }

    private static void register(BEASTState state, String id, StateNode node) {
        node.setID(id);
        state.stateNodes.put(node, TypeToken.of(node.getClass()));
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
