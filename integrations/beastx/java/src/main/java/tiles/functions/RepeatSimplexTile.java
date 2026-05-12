package tiles.functions;

import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import org.phylospec.types.RealScalar;
import org.phylospec.types.Simplex;
import tiles.misc.AssignedArgumentTile;
import tiling.BeastXState;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This tile applies to repeat(value, num) calls that produce a Simplex.
 *
 * Example:
 *
 * repeat(0.25, num=4)
 *
 * This is valid as a Simplex because:
 *
 * 0.25 * 4 = 1.0
 *
 * The output type is org.phylospec.types.Simplex, which can then be used by
 * substitution model tiles such as F81Tile, HKYTile, and GTRTile.
 * RepeatSimplexTile currently supports literal repeat arguments only up tp now.
 */
public class RepeatSimplexTile extends GeneratorTile<Simplex, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "repeat";
    }

    GeneratorTileInput<RealScalar<UnitInterval>, BeastXState> valueInput =
            new GeneratorTileInput<>(
                    "value",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    GeneratorTileInput<Integer, BeastXState> numInput =
            new GeneratorTileInput<>(
                    "num",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public Set<Tile<?, BeastXState>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, BeastXState>>> inputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt {

        Set<Tile<?, BeastXState>> tiles =
                super.tryToTile(node, inputTiles, variableResolver, stochasticityResolver);

        /*
         * Extra check:
         *
         * repeat(value, num) can only produce a Simplex if:
         *
         * value * num == 1.0
         *
         * For example:
         *
         * repeat(0.25, num=4) -> [0.25, 0.25, 0.25, 0.25]
         *
         * The sum is 1.0, so it is a valid Simplex.
         */
        return tiles.stream().filter(tile -> {
            if (!(tile instanceof RepeatSimplexTile simplexTile)) {
                return false;
            }

            /*
             * We first store getTile() as Object because Java generic types are invariant.
             * Directly checking:
             *
             * simplexTile.valueInput.getTile() instanceof AssignedArgumentTile
             *
             * may cause a compile-time incompatible types error.
             */
            Object valueTile = simplexTile.valueInput.getTile();
            Object numTile = simplexTile.numInput.getTile();

            if (!(valueTile instanceof AssignedArgumentTile valueArgTile)) {
                return false;
            }

            if (!(numTile instanceof AssignedArgumentTile numArgTile)) {
                return false;
            }

            if (!(valueArgTile.getRootNode().expression instanceof Expr.Literal valueLiteral)) {
                return false;
            }

            if (!(numArgTile.getRootNode().expression instanceof Expr.Literal numLiteral)) {
                return false;
            }

            if (!(valueLiteral.value instanceof Double value)) {
                return false;
            }

            if (!(numLiteral.value instanceof Integer num)) {
                return false;
            }

            return Math.abs(1.0 - value * num) < 1e-6;
        }).collect(Collectors.toSet());
    }

    @Override
    public Simplex applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        double value = this.valueInput.apply(beastState, indexVariables).get();
        int num = this.numInput.apply(beastState, indexVariables);

        double[] values = new double[num];
        Arrays.fill(values, value);

        return new Simplex() {
            @Override
            public double get(int i) {
                return values[i];
            }

            @Override
            public Double get(int... idx) {
                if (idx.length != 1) {
                    throw new IllegalArgumentException("Simplex requires exactly one index.");
                }
                return get(idx[0]);
            }

            @Override
            public List<UnitInterval> getElements() {
                return Collections.nCopies(values.length, UnitInterval.INSTANCE);
            }

            @Override
            public long size() {
                return values.length;
            }

            @Override
            public UnitInterval domainType() {
                return UnitInterval.INSTANCE;
            }
        };
    }
}