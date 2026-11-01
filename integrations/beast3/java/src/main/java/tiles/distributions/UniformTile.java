package tiles.distributions;

import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Uniform;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BoundDistribution;

public class UniformTile extends GeneratorTile<BoundDistribution<RealScalarParam<Real>, Uniform>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Uniform";
    }

    GeneratorTileInput<RealScalar<? extends Real>, BEASTState> lowerInput = new GeneratorTileInput<>("lower");
    GeneratorTileInput<RealScalar<? extends Real>, BEASTState> upperInput = new GeneratorTileInput<>("upper");

    @Override
    public BoundDistribution<RealScalarParam<Real>, Uniform> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<? extends Real> lower = this.lowerInput.apply(beastState, indexVariables);
        RealScalar<? extends Real> upper = this.upperInput.apply(beastState, indexVariables);

        Uniform distribution = new Uniform();
        // BEAST only reads these bounds. Keep their original objects, domains,
        // and live dependencies on the sampled states.
        @SuppressWarnings("unchecked")
        RealScalar<Real> nativeLower = (RealScalar<Real>) lower;
        @SuppressWarnings("unchecked")
        RealScalar<Real> nativeUpper = (RealScalar<Real>) upper;
        beastState.setInput(distribution, distribution.lowerInput, nativeLower);
        beastState.setInput(distribution, distribution.upperInput, nativeUpper);

        // Read the bounds once for initialization; the prior keeps the original input objects.
        double initialValue = lower.get() / 2.0 + upper.get() / 2.0;
        RealScalarParam<Real> defaultState = new RealScalarParam<>(initialValue, Real.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                stateNode -> beastState.setInput(distribution, distribution.paramInput, stateNode),
                (stateNode, state) -> {
                    RealRandomWalkOperator operator = new RealRandomWalkOperator();
                    state.setInput(operator, operator.scalarInput, stateNode);
                    // An additive proposal can leave zero and cross signs. Only its initial
                    // window is based on these bounds; BEAST checks the current bounds each step.
                    double window = Math.max(Double.MIN_VALUE, upper.get() / 4.0 - lower.get() / 4.0);
                    state.setInput(operator, operator.windowSizeInput, window);
                    state.setInput(operator, operator.m_pWeight, 5.0);
                    return List.of(operator);
                });
    }
}
