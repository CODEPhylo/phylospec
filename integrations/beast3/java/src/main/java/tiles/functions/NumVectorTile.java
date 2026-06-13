package tiles.functions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.Vector;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;
import java.util.Set;

public class NumVectorTile extends GeneratorTile<IntScalarParam<NonNegativeInt>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "num";
    }

    GeneratorTileInput<? extends Vector<?, ?>> vectorInput = new GeneratorTileInput<>(
            "vector",
            Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public IntScalarParam<NonNegativeInt> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Vector<?, ?> vector = this.vectorInput.apply(beastState, indexVariables);
        return new IntScalarParam<>(vector.shape()[0], NonNegativeInt.INSTANCE);
    }

}
