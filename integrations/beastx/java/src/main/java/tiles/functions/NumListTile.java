package tiles.functions;

import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.IntScalar;
import tiling.BeastXIntScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class NumListTile extends GeneratorTile<IntScalar<NonNegativeInt>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "num";
    }

    GeneratorTileInput<? extends List<?>, BeastXState> vectorInput =
            new GeneratorTileInput<>(
                    "vector",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public IntScalar<NonNegativeInt> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        List<?> vector =
                this.vectorInput.apply(beastState, indexVariables);

        return new BeastXIntScalarParam<>(
                vector.size(),
                NonNegativeInt.INSTANCE
        );
    }
}