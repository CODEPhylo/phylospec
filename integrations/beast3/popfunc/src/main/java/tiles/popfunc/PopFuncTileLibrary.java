package tiles.popfunc;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.StateNode;
import beastconfig.BEASTState;
import java.util.Collections;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.phylospec.annotations.ComponentSource;
import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;
import popfunc.beast.evolution.populationmodel.PopFuncWithAVMNOp;
import popfunc.beast.evolution.populationmodel.PopFuncWithUpDownOp;
import popfunc.beast.evolution.populationmodel.PopFuncWithUpOp;
import popfunc.beast.evolution.populationmodel.StochasticVariableSelection;
import tiles.popfunc.generated.GeneratedTileRegistry;

@ComponentSource("/popfunc-components.json")
public final class PopFuncTileLibrary extends TileLibrary<BEASTState> {

    @Override
    public String getId() {
        return "popfunc";
    }

    @Override
    public Class<BEASTState> getStateType() {
        return BEASTState.class;
    }

    @Override
    public List<CandidateTile<BEASTState>> getTiles() {
        List<CandidateTile<BEASTState>> tiles =
                new ArrayList<>(GeneratedTileRegistry.createTiles());
        tiles.add(new ModelIndicatorTile());
        return List.copyOf(tiles);
    }

    @Override
    public List<String> getEngineSpecificationResources() {
        return List.of("/META-INF/phylospec/engines/popfunc.json");
    }

    @Override
    public void configureState(BEASTState state) {
        for (Distribution prior : state.priorDistributions.values()) {
            if (!(prior instanceof Coalescent coalescent)) {
                continue;
            }

            PopulationFunction population = coalescent.popSizeInput.get();
            if (!(coalescent.treeInput.get() instanceof Tree tree) || population == null) {
                continue;
            }

            Set<PopulationFunction> configured =
                    Collections.newSetFromMap(new IdentityHashMap<>());
            configureOperators(population, tree, state, configured);
        }
    }

    private static void configureOperators(
            PopulationFunction population,
            Tree tree,
            BEASTState state,
            Set<PopulationFunction> configured) {
        if (!configured.add(population)) {
            return;
        }

        if (population instanceof StochasticVariableSelection selection) {
            for (PopulationFunction model : selection.modelsInput.get()) {
                configureOperators(model, tree, state, configured);
            }
        }

        if (population instanceof PopFuncWithUpOp provider) {
            addIfApplicable(state, provider.getUpOperator(tree));
        }
        if (population instanceof PopFuncWithUpDownOp provider) {
            addIfApplicable(state, provider.getUpDownOperator1(tree));
            addIfApplicable(state, provider.getUpDownOperator2(tree));
        }
        if (population instanceof PopFuncWithAVMNOp provider) {
            addIfApplicable(state, provider.getAVMNOperator(tree));
        }
    }

    /**
     * Registers a package operator only when every state node it changes belongs to this MCMC
     * state. PopFunc also accepts fixed literal parameters; an operator over one of those literals
     * would otherwise fail BEAST's MCMC sanity check because that parameter is deliberately absent
     * from the state.
     */
    private static void addIfApplicable(BEASTState state, Operator operator) {
        List<StateNode> operatedNodes = operator.listStateNodes();
        if (operatedNodes.isEmpty()
                || !operatedNodes.stream().allMatch(state.stateNodes::containsKey)) {
            return;
        }
        state.addOperator(operator, operatedNodes);
    }
}
