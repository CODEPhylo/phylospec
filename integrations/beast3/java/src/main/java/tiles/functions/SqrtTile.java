package tiles.functions;

import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;

public class SqrtTile extends GeneratorTile<RealScalarParam<NonNegativeReal>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "sqrt";
    }

    GeneratorTileInput<RealScalarParam<? extends NonNegativeReal>, BEASTState> xInput =
            new GeneratorTileInput<>("x", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public RealScalarParam<NonNegativeReal> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        double x = this.xInput.apply(beastState, indexVariables).get();
        return new RealScalarParam<>(Math.sqrt(x), NonNegativeReal.INSTANCE);
    }
}
