package tiles.functions;

import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveInt;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.Real;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.IntScalar;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class LogTile extends GeneratorTile<RealScalar<Real>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "log";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> xInput =
            new GeneratorTileInput<>(
                    "x",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    GeneratorTileInput<IntScalar<? extends PositiveInt>, BeastXState> baseInput =
            new GeneratorTileInput<>(
                    "base",
                    false,
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public RealScalar<Real> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends PositiveReal> x =
                this.xInput.apply(beastState, indexVariables);

        IntScalar<? extends PositiveInt> base =
                this.baseInput.apply(beastState, indexVariables);

        double value;

        if (base == null) {
            value = Math.log(x.get());
        } else {
            if (base.get() == 1) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "Log base must not be 1.",
                        "Use base greater than 1, or omit base for the natural logarithm.",
                        List.of("log(x=8.0, base=2)")
                );
            }

            value = Math.log(x.get()) / Math.log(base.get());
        }

        return new BeastXRealScalarParam<>(value, Real.INSTANCE);
    }
}