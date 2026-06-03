package tiles.functions;

import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.IntScalar;
import org.phylospec.types.Vector;
import tiling.params.BeastXIntScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Set;

public class NumVectorTile extends GeneratorTile<IntScalar<NonNegativeInt>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "num";
    }

    GeneratorTileInput<? extends Vector<?, ?>, BeastXState> vectorInput =
            new GeneratorTileInput<>(
                    "vector",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public IntScalar<NonNegativeInt> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        Vector<?, ?> vector =
                this.vectorInput.apply(beastState, indexVariables);

        return new BeastXIntScalarParam<>(
                vector.shape()[0],
                NonNegativeInt.INSTANCE
        );
    }
}