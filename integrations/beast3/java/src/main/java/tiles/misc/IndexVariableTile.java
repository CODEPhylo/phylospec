package tiles.misc;

import beast.base.spec.domain.Int;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.IntScalar;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import tiles.AstNodeTile;
import tiling.TypeToken;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;

public class IndexVariableTile extends AstNodeTile<IntScalar<Int>, Expr.Variable> {

    @Override
    public Class<Expr.Variable> getTargetNodeType() {
        return Expr.Variable.class;
    }

    @Override
    public IntScalar<Int> applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
        String variableName = this.getRootNode().variableName;
        return new IntScalarParam<>(indexVariables.get(variableName), Int.INSTANCE);
    }

}
