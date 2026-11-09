package tiles.distributions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.Beta;
import beast.base.spec.inference.distribution.IID;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BoundDistribution;

/** Fixed-length IID binding for the native Beta law, preserving each probability's domain. */
public class ProbabilityIIDTile
        extends GeneratorTile<
                BoundDistribution<
                        RealVectorParam<UnitInterval>,
                        IID<RealVectorParam<UnitInterval>, RealScalar<UnitInterval>, Double>>,
                BEASTState> {

    GeneratorTileInput<BoundDistribution<RealScalarParam<UnitInterval>, Beta>, BEASTState> baseInput =
            new GeneratorTileInput<>("base");

    GeneratorTileInput<IntScalarParam<? extends NonNegativeInt>, BEASTState> numInput =
            new GeneratorTileInput<>("num", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public String getPhyloSpecGeneratorName() {
        return "IID";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.distributions");
    }

    @Override
    public BoundDistribution<
                    RealVectorParam<UnitInterval>, IID<RealVectorParam<UnitInterval>, RealScalar<UnitInterval>, Double>>
            applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        var base = baseInput.apply(state, indices);
        int size = numInput.apply(state, indices).get();
        if (size < 1) {
            throw new TileApplicationError(
                    getRootNode(),
                    "IID requires a positive number of elements.",
                    "Provide a fixed positive integer for num.");
        }

        double initial = base.getStateNode().get();
        if (!Double.isFinite(initial) || !UnitInterval.INSTANCE.isValid(initial)) {
            throw new TileApplicationError(
                    getRootNode(),
                    "IID requires an initial probability between zero and one.",
                    "Use a supported Beta base distribution.");
        }
        double[] values = new double[size];
        Arrays.fill(values, initial);
        // These probabilities are independent coordinates, not a sum-to-one simplex.
        RealVectorParam<UnitInterval> parameter = new RealVectorParam<>(values, UnitInterval.INSTANCE);

        // Keep the scalar law and its live inputs without registering a scalar draw.
        var scalarLaw = base.consumeAsBase();
        IID<RealVectorParam<UnitInterval>, RealScalar<UnitInterval>, Double> iid = new IID<>();
        state.setInput(iid, iid.distInput, scalarLaw);

        return new BoundDistribution<>(
                iid,
                parameter,
                value -> {
                    if (value.size() != size) {
                        throw new TileApplicationError(
                                getRootNode(),
                                "The IID vector length does not match num.",
                                "Provide exactly " + size + " elements.");
                    }
                    state.setInput(iid, iid.paramInput, value);
                },
                OperatorSelector::getDefaultOperators);
    }
}
