package tiles.functions;

import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.IntScalar;
import org.phylospec.types.Tensor;
import tiling.params.BeastXIntScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class NumColsTile extends GeneratorTile<IntScalar<NonNegativeInt>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numCols";
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

        if (matrix.isEmpty()) {
            throw new TileApplicationError(
                    "Cannot compute numCols for an empty matrix.",
                    "Use a matrix with at least one row."
            );
        }

        Object firstRow =
                matrix.getFirst();

        if (firstRow instanceof Tensor<?, ?> tensor) {
            int[] shape =
                    tensor.shape();

            if (shape.length != 1) {
                throw new TileApplicationError(
                        "Cannot compute numCols because matrix rows are not vectors.",
                        "Use a matrix such as [[1.0, 2.0], [3.0, 4.0]]."
                );
            }

            return new BeastXIntScalarParam<>(
                    shape[0],
                    NonNegativeInt.INSTANCE
            );
        }

        if (firstRow instanceof List<?> rowList) {
            return new BeastXIntScalarParam<>(
                    rowList.size(),
                    NonNegativeInt.INSTANCE
            );
        }

        throw new TileApplicationError(
                "Cannot compute numCols because matrix rows are not list or tensor rows.",
                "Use a matrix such as [[1.0, 2.0], [3.0, 4.0]]."
        );
    }
}