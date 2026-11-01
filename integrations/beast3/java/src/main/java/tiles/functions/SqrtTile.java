package tiles.functions;

import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.SqrtValue;

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

    /** Keeps a live dependency when the argument depends on a sampled state. */
    public static class Live extends GeneratorTile<SqrtValue, BEASTState> {

        GeneratorTileInput<RealScalar<? extends NonNegativeReal>, BEASTState> xInput =
                new GeneratorTileInput<>("x", Set.of(Stochasticity.STOCHASTIC));

        @Override
        public String getPhyloSpecGeneratorName() {
            return "sqrt";
        }

        @Override
        public SqrtValue applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            RealScalar<? extends NonNegativeReal> x = this.xInput.apply(beastState, indexVariables);
            SqrtValue value = new SqrtValue();
            beastState.setInput(value, value.xInput, x);
            beastState.addCalculationNode(value, new TypeToken<SqrtValue>() {}, this.getId("sqrt", indexVariables, ""));
            beastState.initBEASTObject(value);
            return value;
        }
    }
}
