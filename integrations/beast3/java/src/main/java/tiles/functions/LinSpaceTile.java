package tiles.functions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;

public class LinSpaceTile extends GeneratorTile<RealVectorParam<Real>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "linspace";
    }

    GeneratorTileInput<RealScalar<? extends Real>, BEASTState> startInput =
            new GeneratorTileInput<>("start", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<RealScalar<? extends Real>, BEASTState> endInput =
            new GeneratorTileInput<>("end", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<IntScalarParam<? extends NonNegativeInt>, BEASTState> numInput =
            new GeneratorTileInput<>("num", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public RealVectorParam<Real> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        double start = this.startInput.apply(beastState, indexVariables).get();
        double end = this.endInput.apply(beastState, indexVariables).get();
        int num = this.numInput.apply(beastState, indexVariables).get();

        double[] values = new double[num];

        if (num == 1) {
            values[0] = start;
        } else if (num > 1 && Double.isFinite(start) && Double.isFinite(end)) {
            values[0] = start;
            values[num - 1] = end;
            double span = end - start;
            for (int i = 1; i < num - 1; i++) {
                double fraction = (double) i / (num - 1);
                // Avoid rounding a tiny step to zero before constructing the interior values.
                // If the span overflows, interpolate the opposite-sign endpoints without subtracting them.
                values[i] = Double.isFinite(span) ? start + fraction * span : (1.0 - fraction) * start + fraction * end;
            }
        } else {
            double gap = (end - start) / (num - 1);
            for (int i = 0; i < num; i++) {
                values[i] = start + i * gap;
            }
        }

        return new RealVectorParam<>(values, Real.INSTANCE);
    }
}
