package tiles.functions;

import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import beastconfig.BEASTState;
import tiles.GeneratorTile;

import java.util.IdentityHashMap;
import java.util.Set;

public class LogTile extends GeneratorTile<RealScalarParam<Real>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "log";
    }

    GeneratorTileInput<RealScalarParam<? extends PositiveReal>> xInput = new GeneratorTileInput<>(
            "x", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );
    GeneratorTileInput<IntScalarParam<? extends PositiveInt>> basisInput = new GeneratorTileInput<>(
            "base", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public RealScalarParam<Real> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalarParam<? extends PositiveReal> x = this.xInput.apply(beastState, indexVariables);
        IntScalarParam<? extends PositiveInt> basis = this.basisInput.apply(beastState, indexVariables);

        if (basis == null) {
            // we use the natural logarithm
            return new RealScalarParam<>(Math.log(x.get()), Real.INSTANCE);
        } else {
            // we use the given basis
            return new RealScalarParam<>(Math.log(x.get()) / Math.log(basis.get()), Real.INSTANCE);
        }
    }

}
