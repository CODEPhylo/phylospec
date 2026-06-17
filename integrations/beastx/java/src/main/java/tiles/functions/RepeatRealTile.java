package tiles.functions;

import org.phylospec.ast.AstNode;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.RealScalar;
import org.phylospec.types.RealVector;
import tiling.params.BeastXRealVectorParam;
import tiling.BeastXState;

import java.util.OptionalLong;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Set;

public class RepeatRealTile extends GeneratorTile<RealVector<PositiveReal>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "repeat";
    }

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> valueInput =
            new GeneratorTileInput<>(
                    "value",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    GeneratorTileInput<Integer, BeastXState> numInput =
            new GeneratorTileInput<>(
                    "num",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public RealVector<PositiveReal> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        double value = this.valueInput.apply(beastState, indexVariables).get();
        int num = this.numInput.apply(beastState, indexVariables);

        if (num <= 0) {
            throw new IllegalArgumentException("repeat num must be positive.");
        }

        double[] values = new double[num];
        Arrays.fill(values, value);

        return new BeastXRealVectorParam<>(values, PositiveReal.INSTANCE);
    }

    @Override
    public OptionalLong getFixedOutputSize() {
        return getLiteralIntegerFromTile(this.numInput.getTile());
    }

    private static OptionalLong getLiteralIntegerFromTile(Tile<?, BeastXState> tile) {
        if (tile == null) {
            return OptionalLong.empty();
        }

        AstNode node = tile.getRootNode();

        if (node instanceof Expr.AssignedArgument argument
                && argument.expression instanceof Expr.Literal literal
                && literal.value instanceof Integer value) {
            return OptionalLong.of(value);
        }

        if (node instanceof Expr.Literal literal
                && literal.value instanceof Integer value) {
            return OptionalLong.of(value);
        }

        return OptionalLong.empty();
    }
}