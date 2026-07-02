package tiles.input;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.tiling.tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;
import java.util.Set;

public class FromNewickTile extends GeneratorTile<Tree, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromNewick";
    }

    GeneratorTileInput<String, BEASTState> newickStringInput = new GeneratorTileInput<>(
            "newickString", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public Tree applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String newick = this.newickStringInput.apply(beastState, indexVariables);
        TreeParser treeParser = new TreeParser();
        beastState.setInput(treeParser, treeParser.newickInput, newick);
        beastState.setInput(treeParser, treeParser.adjustTipHeightsInput, false);
        beastState.setInput(treeParser, treeParser.allowSingleChildInput, true);
        beastState.setInput(treeParser, treeParser.isLabelledNewickInput, true);
        beastState.setInput(treeParser, treeParser.offsetInput, 0);
        return treeParser;
    }

}
