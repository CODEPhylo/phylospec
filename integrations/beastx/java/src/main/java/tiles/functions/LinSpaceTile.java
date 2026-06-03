package tiles.functions;

import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.domain.Real;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.IntScalar;
import org.phylospec.types.RealScalar;
import org.phylospec.types.RealVector;
import tiling.params.BeastXRealVectorParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class LinSpaceTile extends GeneratorTile<RealVector<Real>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "linspace";
    }

    GeneratorTileInput<RealScalar<? extends Real>, BeastXState> startInput =
            new GeneratorTileInput<>(
                    "start",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    GeneratorTileInput<RealScalar<? extends Real>, BeastXState> endInput =
            new GeneratorTileInput<>(
                    "end",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    GeneratorTileInput<IntScalar<? extends NonNegativeInt>, BeastXState> numInput =
            new GeneratorTileInput<>(
                    "num",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public RealVector<Real> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        double start = this.startInput.apply(beastState, indexVariables).get();
        double end = this.endInput.apply(beastState, indexVariables).get();
        int num = this.numInput.apply(beastState, indexVariables).get();

        if (num <= 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Empty linspace.",
                    "You use linspace with num less than or equal to 0. Specify a positive num.",
                    List.of("linspace(start=0.0, end=1.0, num=5)")
            );
        }

        double[] values = new double[num];

        if (num == 1) {
            values[0] = start;
        } else {
            double step = (end - start) / (num - 1);

            for (int i = 0; i < num; i++) {
                values[i] = start + i * step;
            }
        }

        return new BeastXRealVectorParam<>(values, Real.INSTANCE);
    }
}