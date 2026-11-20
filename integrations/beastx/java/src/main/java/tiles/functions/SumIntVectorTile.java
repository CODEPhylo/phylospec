package tiles.functions;

import org.phylospec.ast.Expr;
import org.phylospec.domain.Int;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.IntScalar;
import org.phylospec.types.IntVector;
import tiling.params.BeastXIntScalarParam;
import tiling.BeastXState;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * {@code sum(Vector<Integer>)}: the sum of a fixed integer vector.
 *
 * <p>BEAST X samples integer vectors only through indexed statements, which do not produce an
 * integer vector, so only fixed vectors are accepted and the sum is computed once.</p>
 */
public class SumIntVectorTile extends GeneratorTile<IntScalar<Int>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "sum";
    }

    GeneratorTileInput<IntVector<? extends Int>, BeastXState> vectorInput =
            new GeneratorTileInput<>(
                    "vector",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public IntScalar<Int> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        IntVector<? extends Int> vector =
                this.vectorInput.apply(beastState, indexVariables);

        return new BeastXIntScalarParam<>(
                Arrays.stream(vector.getIntArray()).sum(),
                Int.INSTANCE
        );
    }
}
