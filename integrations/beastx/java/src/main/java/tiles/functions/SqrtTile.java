package tiles.functions;

import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.RealScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Set;

public class SqrtTile extends GeneratorTile<RealScalar<NonNegativeReal>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "sqrt";
    }

    GeneratorTileInput<RealScalar<? extends NonNegativeReal>, BeastXState> xInput =
            new GeneratorTileInput<>(
                    "x",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public RealScalar<NonNegativeReal> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends NonNegativeReal> x =
                this.xInput.apply(beastState, indexVariables);

        return new BeastXRealScalarParam<>(
                Math.sqrt(x.get()),
                NonNegativeReal.INSTANCE
        );
    }
}