package tiles.functions;

import beast.base.evolution.tree.Tree;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Optional;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.RootAgeValue;

public class RootAgeTile extends GeneratorTile<RootAgeValue, BEASTState> {

    GeneratorTileInput<Tree, BEASTState> treeInput = new GeneratorTileInput<>("tree");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "rootAge";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.functions.accessor");
    }

    @Override
    public RootAgeValue applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Tree tree = this.treeInput.apply(beastState, indexVariables);

        RootAgeValue value = new RootAgeValue();
        beastState.setInput(value, value.treeInput, tree);
        beastState.addCalculationNode(
                value, new TypeToken<RootAgeValue>() {}, this.getId("rootAge", indexVariables, ""));
        beastState.initBEASTObject(value);

        return value;
    }
}
