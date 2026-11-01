package tiles.functions;

import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.LogValue;

public class LogTile extends GeneratorTile<RealScalarParam<Real>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "log";
    }

    GeneratorTileInput<RealScalarParam<? extends PositiveReal>, BEASTState> xInput =
            new GeneratorTileInput<>("x", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<IntScalarParam<? extends PositiveInt>, BEASTState> basisInput =
            new GeneratorTileInput<>("base", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public RealScalarParam<Real> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalarParam<? extends PositiveReal> x = this.xInput.apply(beastState, indexVariables);
        Integer basis = fixedBase(this.basisInput.apply(beastState, indexVariables), this.getRootNode());

        if (basis == null) {
            // we use the natural logarithm
            return new RealScalarParam<>(Math.log(x.get()), Real.INSTANCE);
        } else {
            // we use the given basis
            return new RealScalarParam<>(Math.log(x.get()) / Math.log(basis), Real.INSTANCE);
        }
    }

    private static Integer fixedBase(IntScalarParam<? extends PositiveInt> basis, AstNode node) {
        if (basis == null) return null;
        int value = basis.get();
        if (value <= 1) {
            throw new TileApplicationError(
                    node,
                    "Logarithm base must be an integer greater than 1.",
                    "Use base=2 or base=10, or omit base for the natural logarithm.");
        }
        return value;
    }

    /** Keeps a live logarithm of a sampled positive scalar, with an optional fixed integer base. */
    public static class Live extends GeneratorTile<LogValue, BEASTState> {

        GeneratorTileInput<RealScalar<? extends PositiveReal>, BEASTState> xInput =
                new GeneratorTileInput<>("x", Set.of(Stochasticity.STOCHASTIC));
        GeneratorTileInput<IntScalarParam<? extends PositiveInt>, BEASTState> basisInput =
                new GeneratorTileInput<>("base", false, Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

        @Override
        public String getPhyloSpecGeneratorName() {
            return "log";
        }

        @Override
        public LogValue applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            RealScalar<? extends PositiveReal> x = this.xInput.apply(beastState, indexVariables);
            Integer basis = fixedBase(this.basisInput.apply(beastState, indexVariables), this.getRootNode());
            LogValue value = new LogValue();
            beastState.setInput(value, value.xInput, x);
            if (basis != null) beastState.setInput(value, value.baseInput, basis);
            beastState.addCalculationNode(value, new TypeToken<LogValue>() {}, this.getId("log", indexVariables, ""));
            beastState.initBEASTObject(value);
            return value;
        }
    }
}
