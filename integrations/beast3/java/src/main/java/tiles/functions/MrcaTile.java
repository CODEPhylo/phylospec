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

public class MrcaTile extends GeneratorTile<MrcaAgeValue, BEASTState> {

    GeneratorTileInput<List<String>, BEASTState> cladeInput =
            new GeneratorTileInput<>("clade", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<Tree, BEASTState> treeInput = new GeneratorTileInput<>("tree");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "mrca";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.functions.accessor");
    }

    @Override
    public MrcaAgeValue applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        List<String> clade = cladeInput.apply(state, indexVariables);
        Tree tree = treeInput.apply(state, indexVariables);

        MrcaAgeValue value = new MrcaAgeValue();
        state.setInput(value, value.treeInput, tree);
        state.setInput(value, value.cladeInput, clade);
        state.addCalculationNode(value, new TypeToken<MrcaAgeValue>() {}, getId("mrca", indexVariables, ""));
        try {
            state.initBEASTObject(value);
        } catch (IllegalArgumentException exception) {
            throw new TileApplicationError(getRootNode(), "Invalid MRCA selection.", exception.getMessage());
        }
        return value;
    }
}
