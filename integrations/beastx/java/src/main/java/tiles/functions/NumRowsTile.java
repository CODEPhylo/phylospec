package tiles.functions;

import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.IntScalar;
import tiling.params.BeastXIntScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class NumRowsTile extends GeneratorTile<IntScalar<NonNegativeInt>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numRows";
    }

    GeneratorTileInput<List<?>, BeastXState> matrixInput =
            new GeneratorTileInput<>(
                    "matrix",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public IntScalar<NonNegativeInt> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        List<?> matrix =
                this.matrixInput.apply(beastState, indexVariables);

        return new BeastXIntScalarParam<>(
                matrix.size(),
                NonNegativeInt.INSTANCE
        );
    }
}