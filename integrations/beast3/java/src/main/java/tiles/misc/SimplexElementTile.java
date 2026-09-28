package tiles.misc;

import beast.base.spec.inference.parameter.VectorElement;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.Simplex;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.AstNodeTile;

/**
 * Tiles {@code w[i]} for a simplex {@code w} into a live BEAST scalar view of that element.
 *
 * <p>{@link IndexedTile} indexes Java lists of already-built objects. A drawn simplex is one
 * BEAST parameter, not a list, and its elements move together under its operators, so an
 * element has to stay a view of the parameter rather than a copy of its current value.
 */
public class SimplexElementTile extends AstNodeTile<RealScalar<?>, Expr.Index, BEASTState> {

    AstNodeTileInput<Simplex, Expr.Index, BEASTState> simplexInput =
            new AstNodeTileInput<>("simplex", expr -> expr.object);
    AstNodeTileInput<? extends IntScalar<?>, Expr.Index, BEASTState> indexInput =
            new AstNodeTileInput<>("index", expr -> expr.indices.getFirst());

    @Override
    public Class<Expr.Index> getTargetNodeType() {
        return Expr.Index.class;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public RealScalar<?> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Simplex simplex = this.simplexInput.apply(beastState, indexVariables);
        int index = this.indexInput.apply(beastState, indexVariables).get();

        if (index < 1 || simplex.size() < index) {
            throw new TileApplicationError(
                    "Index " + index + " is outside the simplex.",
                    "PhyloSpec uses 1-based indexing. Use an index between 1 and " + simplex.size() + ".");
        }

        // index - 1 because PhyloSpec uses 1-indexing and Java uses 0-indexing
        return (RealScalar) new VectorElement(simplex, index - 1);
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return new TypeToken<RealScalar<?>>() {};
    }
}
