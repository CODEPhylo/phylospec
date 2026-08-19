package tiles.substitutionmodels;

import beast.base.spec.evolution.substitutionmodel.JukesCantor;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;

public class JC69Tile extends GeneratorTile<JukesCantor, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "jc69";
    }

    @Override
    public JukesCantor applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return new JukesCantor();
    }
}
