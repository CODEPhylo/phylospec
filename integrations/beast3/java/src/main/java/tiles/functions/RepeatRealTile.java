package tiles.functions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beastconfig.BEASTState;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;

public class RepeatRealTile extends GeneratorTile<RealVectorParam<Real>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "repeat";
    }

    GeneratorTileInput<RealScalarParam<? extends Real>, BEASTState> valueInput =
            new GeneratorTileInput<>("value", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<IntScalarParam<? extends NonNegativeInt>, BEASTState> numInput =
            new GeneratorTileInput<>("num", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public RealVectorParam<Real> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        double value = this.valueInput.apply(beastState, indexVariables).get();
        int num = this.numInput.apply(beastState, indexVariables).get();

        double[] values = new double[num];
        Arrays.fill(values, value);

        return new RealVectorParam<>(values, Real.INSTANCE);
    }

    @Override
    public TypeToken<?> getTypeToken() {
        // extract the domain type arg from RealScalarParam<D> to produce RealVectorParam<D>
        TypeToken<?> domainArg = TypeToken.firstConcreteTypeArg(this.valueInput.getTypeToken());
        if (domainArg != null) return TypeToken.parameterized(RealVectorParam.class, domainArg.getType());

        // we return the basic vector type
        return super.getTypeToken();
    }
}
