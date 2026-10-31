package tiles.distributions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.IID;
import beast.base.spec.inference.distribution.ScalarDistribution;
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

/** IID binding for positive scalar laws with a fixed vector length. */
public class PositiveIIDTile
        extends GeneratorTile<
                BoundDistribution<
                        RealVectorParam<PositiveReal>,
                        IID<RealVectorParam<PositiveReal>, RealScalar<PositiveReal>, Double>>,
                BEASTState> {

    GeneratorTileInput<
                    BoundDistribution<
                            ? extends RealScalarParam<PositiveReal>,
                            ? extends ScalarDistribution<? extends RealScalar<PositiveReal>, Double>>,
                    BEASTState>
            baseInput = new GeneratorTileInput<>("base");

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
                    RealVectorParam<PositiveReal>, IID<RealVectorParam<PositiveReal>, RealScalar<PositiveReal>, Double>>
            applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        var base = this.baseInput.apply(state, indices);
        int size = this.numInput.apply(state, indices).get();

        if (size < 1) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "IID requires a positive number of elements.",
                    "Provide a fixed positive integer for num.");
        }

        double initial = base.getStateNode().get();
        if (!Double.isFinite(initial) || initial <= 0.0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "IID requires a finite positive initial value from its base distribution.",
                    "Use a supported positive scalar distribution such as LogNormal or Gamma.");
        }

        double[] values = new double[size];
        Arrays.fill(values, initial);
        RealVectorParam<PositiveReal> parameter = new RealVectorParam<>(values, PositiveReal.INSTANCE);

        // Preserve the scalar law and its dependencies without drawing a scalar.
        var scalarLaw = base.consumeAsBase();
        IID<RealVectorParam<PositiveReal>, RealScalar<PositiveReal>, Double> iid = new IID<>();
        state.setInput(iid, iid.distInput, scalarLaw);

        return new BoundDistribution<>(
                iid,
                parameter,
                value -> {
                    if (value.size() != size) {
                        throw new TileApplicationError(
                                this.getRootNode(),
                                "The IID vector length does not match num.",
                                "Provide exactly " + size + " elements.");
                    }
                    state.setInput(iid, iid.paramInput, value);
                },
                OperatorSelector::getDefaultOperators);
    }
}
