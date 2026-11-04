package tiles.functions;

import beast.base.evolution.tree.Tree;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.MrcaAgeValue;

/** Reads the current age of a tip selected by its fixed, exact identifier. */
public class TipAgeTile extends GeneratorTile<MrcaAgeValue, BEASTState> {

    GeneratorTileInput<String, BEASTState> nodeInput =
            new GeneratorTileInput<>("node", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<Tree, BEASTState> treeInput = new GeneratorTileInput<>("tree");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "age";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.functions.accessor");
    }

    @Override
    public MrcaAgeValue applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String name = nodeInput.apply(state, indexVariables);
        Tree tree = treeInput.apply(state, indexVariables);
        if (name == null || name.isBlank()) {
            throw new TileApplicationError(
                    getRootNode(), "Invalid tip-age selection.", "Specify a nonblank, exact tree-tip identifier.");
        }

        // The most recent common ancestor of a single tip is the tip itself.
        MrcaAgeValue value = new MrcaAgeValue();
        state.setInput(value, value.treeInput, tree);
        state.setInput(value, value.cladeInput, List.of(name));
        state.addCalculationNode(value, new TypeToken<MrcaAgeValue>() {}, getId("age", indexVariables, ""));
        try {
            state.initBEASTObject(value);
        } catch (IllegalArgumentException exception) {
            throw new TileApplicationError(getRootNode(), "Invalid tip-age selection.", exception.getMessage());
        }
        return value;
    }
}
