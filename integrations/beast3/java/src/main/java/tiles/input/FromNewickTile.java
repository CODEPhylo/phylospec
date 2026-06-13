package tiles.input;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;
import java.util.Set;

public class FromNewickTile extends GeneratorTile<Tree> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "fromNewick";
    }

    GeneratorTileInput<String> newickStringInput = new GeneratorTileInput<>(
            "newickString", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public Tree applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        String newick = this.newickStringInput.apply(beastState, indexVariables);
        return new TreeParser(newick);
    }

}
