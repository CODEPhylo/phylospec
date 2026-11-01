package tiles.functions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.ExpValue;

public class ExpTile extends GeneratorTile<RealScalarParam<PositiveReal>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "exp";
    }

    GeneratorTileInput<RealScalarParam<? extends Real>, BEASTState> xInput =
            new GeneratorTileInput<>("x", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public RealScalarParam<PositiveReal> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalarParam<? extends Real> variable = this.xInput.apply(beastState, indexVariables);
        return new RealScalarParam<>(Math.exp(variable.get()), PositiveReal.INSTANCE);
    }

    /** Keeps a live dependency when the argument depends on a sampled state. */
    public static class Live extends GeneratorTile<ExpValue, BEASTState> {

        GeneratorTileInput<RealScalar<? extends Real>, BEASTState> xInput =
                new GeneratorTileInput<>("x", Set.of(Stochasticity.STOCHASTIC));

        @Override
        public String getPhyloSpecGeneratorName() {
            return "exp";
        }

        @Override
        public ExpValue applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            RealScalar<? extends Real> x = this.xInput.apply(beastState, indexVariables);
            ExpValue value = new ExpValue();
            beastState.setInput(value, value.xInput, x);
            beastState.addCalculationNode(value, new TypeToken<ExpValue>() {}, this.getId("exp", indexVariables, ""));
            beastState.initBEASTObject(value);
            return value;
        }
    }
}
