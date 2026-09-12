package tiles.popfunc;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.coalescent.Coalescent;
import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.inference.Distribution;
import beastconfig.BEASTState;
import java.util.Collections;
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
        return GeneratedTileRegistry.createTiles();
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
            state.addOperator(provider.getUpOperator(tree), tree);
        }
        if (population instanceof PopFuncWithUpDownOp provider) {
            state.addOperator(provider.getUpDownOperator1(tree), tree);
            state.addOperator(provider.getUpDownOperator2(tree), tree);
        }
        if (population instanceof PopFuncWithAVMNOp provider) {
            state.addOperator(provider.getAVMNOperator(tree), tree);
        }
    }
}
