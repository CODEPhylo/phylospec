package tiles.functions;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.IntVectorParam;
import beastconfig.BEASTState;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.stream.IntStream;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;

public class RepeatIntTile extends GeneratorTile<IntVectorParam<Int>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "repeat";
    }

    GeneratorTileInput<IntScalarParam<? extends Int>, BEASTState> valueInput =
            new GeneratorTileInput<>("value", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<IntScalarParam<? extends NonNegativeInt>, BEASTState> numInput =
            new GeneratorTileInput<>("num", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public TypeToken<?> getTypeToken() {
        // extract the domain type arg from IntScalarParam<D> to produce IntVectorParam<D>
        TypeToken<?> domainArg = TypeToken.firstConcreteTypeArg(this.valueInput.getTypeToken());
        if (domainArg != null) return TypeToken.parameterized(IntVectorParam.class, domainArg.getType());

        // we return the basic vector type
        return super.getTypeToken();
    }

    @Override
    public IntVectorParam<Int> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        IntScalarParam<? extends Int> value = this.valueInput.apply(beastState, indexVariables);
        int num = this.numInput.apply(beastState, indexVariables).get();
        Int domain = value.getDomain();

        int[] values = new int[num];
        Arrays.fill(values, value.get());

        IntVectorParam<Int> result = new IntVectorParam<>();
        result.domainTypeInput.setValue(domain, result);

        // Match the vector bounds to the input domain before validation.
        result.setLower(domain.getLower());
        result.setUpper(domain.getUpper());

        result.valuesInput.setValue(IntStream.of(values).boxed().toList(), result);
        result.initAndValidate();
        return result;
    }
}
