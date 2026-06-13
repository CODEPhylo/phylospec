package tiles.functions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.RealScalarParam;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;
import java.util.Set;

public class ExpTile extends GeneratorTile<RealScalarParam<PositiveReal>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "exp";
    }

    GeneratorTileInput<RealScalarParam<? extends Real>> xInput = new GeneratorTileInput<>(
            "x", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public RealScalarParam<PositiveReal> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalarParam<? extends Real> variable = this.xInput.apply(beastState, indexVariables);
        return new RealScalarParam<>(Math.exp(variable.get()), PositiveReal.INSTANCE);
    }

}
