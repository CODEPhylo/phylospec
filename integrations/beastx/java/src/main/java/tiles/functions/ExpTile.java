package tiles.functions;

import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.Real;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Set;

public class ExpTile extends GeneratorTile<RealScalar<PositiveReal>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "exp";
    }

    GeneratorTileInput<RealScalar<? extends Real>, BeastXState> xInput =
            new GeneratorTileInput<>(
                    "x",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public RealScalar<PositiveReal> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends Real> x =
                this.xInput.apply(beastState, indexVariables);

        return new BeastXRealScalarParam<>(
                Math.exp(x.get()),
                PositiveReal.INSTANCE
        );
    }
}
