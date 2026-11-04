package tiles.distributions;

import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.Simplex;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BoundDistribution;
import tiling.NormalMixtureDistribution;

/** A fixed number of Normal components with live parameters and simplex weights. */
public class MixtureTile
        extends GeneratorTile<BoundDistribution<RealScalarParam<Real>, NormalMixtureDistribution>, BEASTState> {

    GeneratorTileInput<List<BoundDistribution<RealScalarParam<Real>, Normal>>, BEASTState> componentsInput =
            new GeneratorTileInput<>("components");
    GeneratorTileInput<Simplex, BEASTState> weightsInput = new GeneratorTileInput<>("weights");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Mixture";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.distributions");
    }

    @Override
    public BoundDistribution<RealScalarParam<Real>, NormalMixtureDistribution> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        var components = componentsInput.apply(state, indices);
        Simplex weights = weightsInput.apply(state, indices);
        List<Normal> nativeComponents = new ArrayList<>();
        for (var component : components) {
            // Consume each law, without registering its default draw or prior.
            nativeComponents.add(component.consumeAsBase());
        }

        NormalMixtureDistribution distribution = new NormalMixtureDistribution();
        state.setInput(distribution, distribution.componentsInput, nativeComponents);
        state.setInput(distribution, distribution.weightsInput, weights);

        double initialValue;
        try {
            distribution.validateMixture();
            int largestWeight = 0;
            for (int i = 1; i < weights.size(); i++) {
                if (weights.get(i) > weights.get(largestWeight)) largestWeight = i;
            }
            // Start at the mode of a positive-weight component, not between distant modes.
            initialValue = nativeComponents.get(largestWeight).getMean();
            if (!Double.isFinite(initialValue)) {
                throw new IllegalArgumentException("The selected Normal component must have a finite mean.");
            }
        } catch (IllegalArgumentException exception) {
            throw new TileApplicationError(
                    getRootNode(), "Cannot initialize this Normal Mixture.", exception.getMessage());
        }

        RealScalarParam<Real> parameter = new RealScalarParam<>(initialValue, Real.INSTANCE);
        return new BoundDistribution<>(
                distribution,
                parameter,
                value -> {
                    state.initBEASTObject(value);
                    // All component densities describe the same value. Only the mixture is a posterior factor.
                    for (Normal component : nativeComponents) {
                        state.setInput(component, component.paramInput, value);
                    }
                    state.setInput(distribution, distribution.paramInput, value);
                },
                (value, graph) -> {
                    RealRandomWalkOperator operator = new RealRandomWalkOperator();
                    graph.setInput(operator, operator.scalarInput, value);
                    graph.setInput(operator, operator.m_pWeight, 5.0);
                    return List.of(operator);
                });
    }
}
