package tiles.misc;

import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.parameter.VectorElement;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.Simplex;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.typeresolver.Stochasticity;

/**
 * Tiles {@code w[i]} for a simplex {@code w} into a live BEAST scalar view of that element.
 *
 * <p>{@link IndexedTile} indexes Java lists of already-built objects. A drawn simplex is one
 * BEAST parameter, not a list, and its elements move together under its operators, so an
 * element has to stay a view of the parameter rather than a copy of its current value.
 */
public class SimplexElementTile extends AstNodeTile<RealScalar<UnitInterval>, Expr.Index, BEASTState> {

    AstNodeTileInput<Simplex, Expr.Index, BEASTState> simplexInput =
            new AstNodeTileInput<>("simplex", expr -> expr.object);
    AstNodeTileInput<? extends IntScalar<?>, Expr.Index, BEASTState> indexInput = new AstNodeTileInput<>(
            "index", expr -> expr.indices.getFirst(), Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public Class<Expr.Index> getTargetNodeType() {
        return Expr.Index.class;
    }

    @Override
    public RealScalar<UnitInterval> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        if (getRootNode().indices.size() != 1) {
            throw new TileApplicationError(getRootNode(), "A simplex takes one index.", "Use w[i].");
        }
        Simplex simplex = this.simplexInput.apply(beastState, indexVariables);
        int index = this.indexInput.apply(beastState, indexVariables).get();

        if (index < 1 || simplex.size() < index) {
            throw new TileApplicationError(
                    "Index " + index + " is outside the simplex.",
                    "PhyloSpec uses 1-based indexing. Use an index between 1 and " + simplex.size() + ".");
        }

        // index - 1 because PhyloSpec uses 1-indexing and Java uses 0-indexing
        return new VectorElement<>(simplex, index - 1);
    }
}
