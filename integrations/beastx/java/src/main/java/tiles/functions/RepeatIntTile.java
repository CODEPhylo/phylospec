package tiles.functions;

import org.phylospec.ast.Expr;
import org.phylospec.domain.Int;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.IntScalar;
import org.phylospec.types.IntVector;
import tiling.BeastXIntVectorParam;
import tiling.BeastXState;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Set;

public class RepeatIntTile extends GeneratorTile<IntVector<Int>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "repeat";
    }

    GeneratorTileInput<IntScalar<? extends Int>, BeastXState> valueInput =
            new GeneratorTileInput<>(
                    "value",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    GeneratorTileInput<IntScalar<? extends NonNegativeInt>, BeastXState> numInput =
            new GeneratorTileInput<>(
                    "num",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public IntVector<Int> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        int value = this.valueInput.apply(beastState, indexVariables).get();
        int num = this.numInput.apply(beastState, indexVariables).get();

        if (num <= 0) {
            throw new IllegalArgumentException("repeat num must be positive.");
        }

        int[] values = new int[num];
        Arrays.fill(values, value);

        return new BeastXIntVectorParam<>(values, Int.INSTANCE);
    }

    @Override
    public TypeToken<?> getTypeToken() {
        TypeToken<?> domainArg =
                TypeToken.firstConcreteTypeArg(this.valueInput.getTypeToken());

        if (domainArg != null) {
            return TypeToken.parameterized(IntVector.class, domainArg.getType());
        }

        return super.getTypeToken();
    }
}
