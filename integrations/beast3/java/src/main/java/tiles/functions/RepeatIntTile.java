package tiles.functions;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.IntVectorParam;
import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.TypeToken;

import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Set;

public class RepeatIntTile extends GeneratorTile<IntVectorParam<Int>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "repeat";
    }

    GeneratorTileInput<IntScalarParam<? extends Int>> valueInput = new GeneratorTileInput<>(
            "value", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );
    GeneratorTileInput<IntScalarParam<? extends NonNegativeInt>> numInput = new GeneratorTileInput<>(
            "num",
            Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public TypeToken<?> getTypeToken() {
        // extract the domain type arg from IntScalarParam<D> to produce IntVectorParam<D>
        TypeToken<?> valueType = this.valueInput.getTypeToken();
        if (valueType != null && valueType.getType() instanceof ParameterizedType pt) {
            return TypeToken.parameterized(IntVectorParam.class, pt.getActualTypeArguments()[0]);
        }

        // we return the basic vector type
        return super.getTypeToken();
    }

    @Override
    public IntVectorParam<Int> applyTile(BEASTState beastState) {
        int value = this.valueInput.apply(beastState).get();
        int num = this.numInput.apply(beastState).get();

        int[] values = new int[num];
        Arrays.fill(values, value);

        return new IntVectorParam<>(values, Int.INSTANCE);
    }

}
