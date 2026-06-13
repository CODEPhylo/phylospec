package tiles.functions;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;

public class NumTaxaTreeTile extends GeneratorTile<IntScalarParam<NonNegativeInt>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numTaxa";
    }

    GeneratorTileInput<Tree> treeInput = new GeneratorTileInput<>("tree");

    @Override
    public IntScalarParam<NonNegativeInt> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Tree tree = this.treeInput.apply(beastState, indexVariables);
        return new IntScalarParam<>(tree.getTaxaNames().length, NonNegativeInt.INSTANCE);
    }

}
