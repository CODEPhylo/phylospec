package tiles.distributions;

import org.phylospec.ast.AstNode;
import org.phylospec.domain.Real;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.GeneratorTile.GeneratorTileInput;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.types.RealScalar;
import tiling.BeastXState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A real argument that also accepts a sampled variable of a subtype of Real, such as a
 * PositiveReal or a Probability, as in BEAST 3.
 *
 * <p>A literal or fixed value is tiled as several equivalent scalars, one per domain (Real,
 * NonNegativeReal, PositiveReal, UnitInterval). Accepting all of them would multiply the
 * candidate tilings of every model, so whenever a value can be used as a plain Real, only those
 * tilings are kept, exactly as with a {@code RealScalar<Real>} input. Other domains are used only
 * when nothing else fits.</p>
 */
class RealArgumentInput extends GeneratorTileInput<RealScalar<? extends Real>, BeastXState> {

    private static final TypeToken<RealScalar<Real>> REAL = new TypeToken<RealScalar<Real>>() {};

    RealArgumentInput(String phylospecArgumentName) {
        super(phylospecArgumentName);
    }

    @Override
    public Set<Tile<?, BeastXState>> getCompatibleInputTiles(
            AstNode inputAstNode,
            Map<AstNode, Set<Tile<?, BeastXState>>> possibleInputTiles,
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt.RejectedCascade, FailedTilingAttempt.RejectedBoundary {
        Set<Tile<?, BeastXState>> compatible =
                super.getCompatibleInputTiles(inputAstNode, possibleInputTiles, stochasticityResolver);

        Set<Tile<?, BeastXState>> plainReal = new HashSet<>();
        for (Tile<?, BeastXState> tile : compatible) {
            if (REAL.isAssignableFrom(tile.getTypeToken())) {
                plainReal.add(tile);
            }
        }

        return plainReal.isEmpty() ? compatible : plainReal;
    }
}
