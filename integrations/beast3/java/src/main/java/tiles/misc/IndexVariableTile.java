package tiles.misc;

import beast.base.spec.domain.Int;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.IntScalar;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import tiles.AstNodeTile;

import java.util.IdentityHashMap;

public class IndexVariableTile extends AstNodeTile<IntScalar<Int>, Expr.Variable> {

    @Override
    public IntScalar<Int> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        // we find the index variable of the definition

        for (Expr.Variable definition : this.getIndexVariables()) {
            if (definition.variableName.equals(this.getRootNode().variableName)) {
                return new IntScalarParam<>(indexVariables.get(definition), Int.INSTANCE);
            }
        }

        throw new RuntimeException();
    }

}
