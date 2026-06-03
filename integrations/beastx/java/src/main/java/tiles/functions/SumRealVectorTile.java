package tiles.functions;

import dr.inference.model.Parameter;
import dr.inference.model.Statistic;
import dr.inference.model.SumStatistic;
import org.phylospec.ast.Expr;
import org.phylospec.domain.Real;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.RealVector;
import tiling.params.BeastXParam;
import tiling.rpn.BeastXRPNCalculationResult;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Set;

public class SumRealVectorTile extends GeneratorTile<BeastXRPNCalculationResult, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "sum";
    }

    GeneratorTileInput<RealVector<? extends Real>, BeastXState> vectorInput =
            new GeneratorTileInput<>(
                    "vector",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC, Stochasticity.STOCHASTIC)
            );

    @Override
    public BeastXRPNCalculationResult applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealVector<? extends Real> vector =
                this.vectorInput.apply(beastState, indexVariables);

        Statistic vectorStatistic =
                toStatistic(vector);

        String sumId =
                beastState.getAvailableID("sum");

        SumStatistic sumStatistic =
                new SumStatistic(
                        sumId,
                        false,
                        new double[0]
                );

        sumStatistic.addStatistic(vectorStatistic);

        return BeastXRPNCalculationResult.from(
                sumStatistic,
                beastState
        );
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<BeastXRPNCalculationResult>() {};
    }

    private static Statistic toStatistic(RealVector<? extends Real> vector) {
        if (vector instanceof BeastXParam beastXParam) {
            return beastXParam.getParameter();
        }

        return new Parameter.Default(toArray(vector));
    }

    private static double[] toArray(RealVector<? extends Real> vector) {
        double[] values =
                new double[Math.toIntExact(vector.size())];

        for (int i = 0; i < values.length; i++) {
            values[i] = vector.get(i);
        }

        return values;
    }
}