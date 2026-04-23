package tiles.functions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class NumListTile extends GeneratorTile<IntScalarParam<NonNegativeInt>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "num";
    }

    GeneratorTileInput<? extends List<?>> vectorInput = new GeneratorTileInput<>(
            "vector",
            Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public IntScalarParam<NonNegativeInt> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        List<?> vector = this.vectorInput.apply(beastState, indexVariables);
        return new IntScalarParam<>(vector.size(), NonNegativeInt.INSTANCE);
    }

}
