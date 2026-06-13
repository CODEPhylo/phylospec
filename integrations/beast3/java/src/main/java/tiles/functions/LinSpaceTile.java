package tiles.functions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;
import java.util.Set;

public class LinSpaceTile extends GeneratorTile<RealVectorParam<Real>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "linspace";
    }

    GeneratorTileInput<RealScalarParam<? extends Real>> startInput = new GeneratorTileInput<>(
            "start", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );
    GeneratorTileInput<RealScalarParam<? extends Real>> endInput = new GeneratorTileInput<>(
            "end", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );
    GeneratorTileInput<IntScalarParam<? extends NonNegativeInt>> numInput = new GeneratorTileInput<>(
            "num", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public RealVectorParam<Real> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        double start = this.startInput.apply(beastState, indexVariables).get();
        double end = this.endInput.apply(beastState, indexVariables).get();
        int num = this.numInput.apply(beastState, indexVariables).get();

        double[] values = new double[num];

        if (num == 1) {
            values[0] = start;
        } else {
            double gap = (end - start) / (num - 1);
            for (int i = 0; i < num; i++) {
                values[i] = start + i * gap;
            }
        }

        return new RealVectorParam<>(values, Real.INSTANCE);
    }

}
