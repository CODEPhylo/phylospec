package tiles.substitutionmodels;

import beast.base.spec.evolution.substitutionmodel.JukesCantor;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;

public class JC69Tile extends GeneratorTile<JukesCantor> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "jc69";
    }

    @Override
    public JukesCantor applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return new JukesCantor();
    }

}
