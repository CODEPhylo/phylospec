package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.model.Parameter;
import dr.util.Attribute;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveInt;
import org.phylospec.domain.Real;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.IntScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXRealVectorParam;
import tiling.BeastXState;
import tiling.BoundDistribution;

import java.util.Arrays;
import java.util.IdentityHashMap;

public class IIDTile extends GeneratorTile<
        BoundDistribution<BeastXRealVectorParam<Real>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "IID";
    }

    GeneratorTileInput<
            BoundDistribution<BeastXRealScalarParam<Real>, DistributionLikelihood>,
            BeastXState
            > baseInput =
            new GeneratorTileInput<>("base");

    GeneratorTileInput<IntScalar<? extends PositiveInt>, BeastXState> numInput =
            new GeneratorTileInput<>("num");

    @Override
    public BoundDistribution<BeastXRealVectorParam<Real>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        BoundDistribution<BeastXRealScalarParam<Real>, DistributionLikelihood> base =
                this.baseInput.apply(beastState, indexVariables);

        IntScalar<? extends PositiveInt> num =
                this.numInput.apply(beastState, indexVariables);

        int dimension =
                num.get();

        if (dimension <= 0) {
            throw new TileApplicationError(
                    "IID num must be positive.",
                    "Use num >= 1."
            );
        }

        double defaultValue =
                base.stateNode.getParameter().getParameterValue(0);

        double[] defaultValues =
                new double[dimension];

        Arrays.fill(
                defaultValues,
                defaultValue
        );

        BeastXRealVectorParam<Real> defaultState =
                new BeastXRealVectorParam<>(
                        new Parameter.Default(defaultValues),
                        Real.INSTANCE
                );

        return new BoundDistribution<>(
                base.distribution,
                defaultState,
                state -> base.distribution.addData(new Attribute.Default<>(
                        state.getParameter().getParameterName(),
                        state.getParameter().getParameterValues()
                ))
        );
    }
}