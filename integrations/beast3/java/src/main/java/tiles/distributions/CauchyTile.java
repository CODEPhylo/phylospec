package tiles.distributions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Cauchy;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BoundDistribution;

public class CauchyTile extends GeneratorTile<BoundDistribution<RealScalarParam<Real>, Cauchy>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Cauchy";
    }

    GeneratorTileInput<RealScalar<? extends Real>, BEASTState> locationInput = new GeneratorTileInput<>("location");
    GeneratorTileInput<RealScalar<PositiveReal>, BEASTState> scaleInput = new GeneratorTileInput<>("scale");

    @Override
    public BoundDistribution<RealScalarParam<Real>, Cauchy> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<? extends Real> location = this.locationInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> scale = this.scaleInput.apply(beastState, indexVariables);

        Cauchy distribution = new Cauchy();

        // BEAST only reads the location through this input. Keep the original
        // object, including its domain and live dependency on the sampled state.
        @SuppressWarnings("unchecked")
        RealScalar<Real> nativeLocation = (RealScalar<Real>) location;
        beastState.setInput(distribution, distribution.locationInput, nativeLocation);
        beastState.setInput(distribution, distribution.scaleInput, scale);

        RealScalarParam<Real> defaultState = new RealScalarParam<>();

        return new BoundDistribution<>(
                distribution,
                defaultState,
                stateNode -> beastState.setInput(distribution, distribution.paramInput, stateNode),
                (value, graph) -> {
                    // Additive proposals can leave zero and cross signs.
                    RealRandomWalkOperator operator = new RealRandomWalkOperator();
                    graph.setInput(operator, operator.scalarInput, value);
                    graph.setInput(operator, operator.m_pWeight, 5.0);
                    return List.of(operator);
                });
    }
}
