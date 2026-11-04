package tiles.distributions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.distribution.IID;
import beast.base.spec.inference.distribution.Poisson;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.type.IntScalar;
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

/** Fixed-length IID binding for the currently supported Poisson base distribution. */
public class NonNegativeIntegerIIDTile
        extends GeneratorTile<
                BoundDistribution<
                        IntVectorParam<NonNegativeInt>,
                        IID<IntVectorParam<NonNegativeInt>, IntScalar<NonNegativeInt>, Integer>>,
                BEASTState> {

    GeneratorTileInput<BoundDistribution<? extends IntScalarParam<NonNegativeInt>, ? extends Poisson>, BEASTState>
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
                    IntVectorParam<NonNegativeInt>,
                    IID<IntVectorParam<NonNegativeInt>, IntScalar<NonNegativeInt>, Integer>>
            applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        var base = this.baseInput.apply(state, indices);
        int size = this.numInput.apply(state, indices).get();

        if (size < 1) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "IID requires a positive number of elements.",
                    "Provide a fixed positive integer for num.");
        }

        int initial = base.getStateNode().get();
        if (initial < 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "IID requires a non-negative initial value from its base distribution.",
                    "Use Poisson as the base distribution.");
        }

        int[] values = new int[size];
        Arrays.fill(values, initial);
        IntVectorParam<NonNegativeInt> parameter = new IntVectorParam<>(values, NonNegativeInt.INSTANCE);
        // The native random walk reads these legacy bounds, not the domain directly.
        parameter.setLower(NonNegativeInt.INSTANCE.getLower());
        parameter.setUpper(NonNegativeInt.INSTANCE.getUpper());

        // Preserve the scalar law and its dependencies without drawing a scalar.
        var scalarLaw = base.consumeAsBase();
        IID<IntVectorParam<NonNegativeInt>, IntScalar<NonNegativeInt>, Integer> iid = new IID<>();
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
